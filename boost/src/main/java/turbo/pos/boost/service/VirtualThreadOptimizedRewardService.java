package turbo.pos.boost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import turbo.pos.boost.diagnostics.RewardPhaseTiming;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.exception.RedisUnavailableException;
import turbo.pos.boost.redis.AtomicLockReleaseLuaExecutor;
import turbo.pos.boost.redis.RewardCheckoutLuaExecutor;
import turbo.pos.boost.redis.RewardCheckoutLuaExecutor.LuaCheckoutResult;
import turbo.pos.boost.util.RedisUtils;
import turbo.pos.boost.util.RewardUtils;
import turbo.pos.common.RewardOutboxEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Redis path dùng SET NX thay RLock để tránh carrier thread pinning với virtual threads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rewards.optimized", havingValue = "true")
public class VirtualThreadOptimizedRewardService {

    private static final String HASH_KEY = RewardCheckoutLuaExecutor.HASH_KEY;
    private static final String OUTBOX_KEY = RewardCheckoutLuaExecutor.OUTBOX_KEY;
    private static final String EXPECTED_PREFIX = RewardCheckoutLuaExecutor.EXPECTED_PREFIX;
    private static final String IDEM_PREFIX = RewardCheckoutLuaExecutor.IDEM_PREFIX;
    private static final String LOCK_PREFIX = "reward:lock:";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final RewardCheckoutLuaExecutor rewardCheckoutLuaExecutor;
    private final AtomicLockReleaseLuaExecutor atomicLockReleaseLuaExecutor;

    @Value("${app.rewards.lock.wait-millis:5000}")
    private long lockWaitMillis;

    @Value("${app.rewards.lock.lease-seconds:30}")
    private long lockLeaseSeconds;

    @Value("${app.rewards.lock.retry-delay-millis:10}")
    private long retryDelayMillis;

    @Value("${app.rewards.idempotency.ttl-days:7}")
    private int idempotencyTtlDays;

    @Value("${app.rewards.use-lua:true}")
    private boolean useLua;

    @PostConstruct
    void logModes() {
        log.info("VirtualThreadOptimizedRewardService initialized");
        if (useLua) {
            log.info("Reward checkout uses Lua script (idem + points + outbox in one round-trip after lock)");
        }
    }

    public RewardResponse processReward(TransactionRequest request) {
        long start = System.currentTimeMillis();
        long t0 = System.nanoTime();
        
        String customerId = request.getCustomerId();
        String txnId = request.getTransactionId();
        String lockKey = LOCK_PREFIX + customerId;
        String lockValue = UUID.randomUUID().toString(); // Unique lock identifier
        boolean acquired = false;
        long lockAcquiredAt = 0; // Track lock acquisition time for validity checking

        try {
            // Acquire lock using Redis SET NX
            long tLockStart = System.nanoTime();
            acquired = acquireLock(lockKey, lockValue);
            lockAcquiredAt = System.currentTimeMillis();
            recordPhase("lockMs", tLockStart);
            
            if (!acquired) {
                return RewardResponse.builder()
                        .customerId(customerId)
                        .totalPoints(0L)
                        .status("LOCK_FAILED")
                        .threadName(Thread.currentThread().toString())
                        .processingTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            if (!isLockValid(lockKey, lockValue, lockAcquiredAt)) {
                log.error("Lock expired before processing for customer {}", customerId);
                return RewardResponse.builder()
                        .customerId(customerId)
                        .totalPoints(0L)
                        .status("LOCK_EXPIRED")
                        .threadName(Thread.currentThread().toString())
                        .processingTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            if (useLua) {
                return processRewardWithLua(request, customerId, txnId, start, t0, lockKey, lockValue, lockAcquiredAt);
            }
            return processRewardMultiCall(request, customerId, txnId, start, t0, lockKey, lockValue, lockAcquiredAt);
            
        } catch (Exception e) {
            if (RedisUtils.isRedisUnavailable(e)) {
                log.error("VirtualThreadOptimizedRewardService: Redis unavailable -> circuit breaker fallback", e);
                throw new RedisUnavailableException("Redis unavailable", e);
            }
            log.error("VirtualThreadOptimizedRewardService.processReward failed", e);
            return RewardResponse.builder()
                    .customerId(customerId)
                    .totalPoints(0L)
                    .status("ERROR")
                    .threadName(Thread.currentThread().toString())
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            if (acquired) {
                releaseLock(lockKey, lockValue);
            }
        }
    }

    private boolean acquireLock(String lockKey, String lockValue) {
        long deadline = System.currentTimeMillis() + lockWaitMillis;

        while (System.currentTimeMillis() < deadline) {
            RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
            boolean acquired = bucket.setIfAbsent(lockValue, Duration.ofSeconds(lockLeaseSeconds));

            if (acquired) {
                return true;
            }

            try {
                Thread.sleep(retryDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return false;
    }

    /**
     * Release lock atomically via Lua script (check ownership + delete in one round-trip).
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValue);
            
            if (!released) {
                log.debug("Lock {} not released - not owned by us (value: {})", lockKey, lockValue);
            }
        } catch (Exception e) {
            log.warn("Error releasing lock {}: {}", lockKey, e.getMessage());
        }
    }

    /** Fail fast if lock lease is nearly exhausted or value no longer matches. */
    private boolean isLockValid(String lockKey, String lockValue, long lockAcquiredAt) {
        long elapsed = System.currentTimeMillis() - lockAcquiredAt;
        long safetyThreshold = lockLeaseSeconds * 1000 * 80 / 100; // 80% of lease time
        
        if (elapsed >= safetyThreshold) {
            log.warn("Lock {} approaching expiration ({}ms elapsed, threshold {}ms)", 
                    lockKey, elapsed, safetyThreshold);
            return false;
        }
        
        // Verify lock still exists with correct value
        try {
            RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
            String currentValue = bucket.get();
            if (!lockValue.equals(currentValue)) {
                log.warn("Lock {} value mismatch (expected: {}, actual: {})", 
                        lockKey, lockValue, currentValue);
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to verify lock {}: {}", lockKey, e.getMessage());
            return false;
        }
        
        return true;
    }

    private RewardResponse processRewardWithLua(
            TransactionRequest request,
            String customerId,
            String txnId,
            long start,
            long t0,
            String lockKey,
            String lockValue,
            long lockAcquiredAt) throws Exception {
        long pointsDelta = RewardUtils.calculatePoints(request.getAmount());
        String outboxJson = objectMapper.writeValueAsString(new RewardOutboxEvent(
                customerId,
                txnId,
                request.getAmount(),
                pointsDelta,
                OffsetDateTime.now()
        ));
        long idemTtlSec = Duration.ofDays(idempotencyTtlDays).toSeconds();

        long tLua = System.nanoTime();
        LuaCheckoutResult lua = rewardCheckoutLuaExecutor.execute(
                customerId, txnId, pointsDelta, outboxJson, idemTtlSec);
        recordPhase("luaMs", tLua);
        recordPhase("totalMs", t0);

        if (lua.duplicate()) {
            return RewardResponse.builder()
                    .customerId(customerId)
                    .totalPoints(lua.totalPoints())
                    .status("DUPLICATE_TRANSACTION")
                    .threadName(Thread.currentThread().toString())
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        }

        return RewardResponse.builder()
                .customerId(customerId)
                .totalPoints(lua.totalPoints())
                .status("SUCCESS")
                .threadName(Thread.currentThread().toString())
                .processingTimeMs(System.currentTimeMillis() - start)
                .build();
    }

    private RewardResponse processRewardMultiCall(
            TransactionRequest request,
            String customerId,
            String txnId,
            long start,
            long t0,
            String lockKey,
            String lockValue,
            long lockAcquiredAt) throws Exception {
        String idemKey = IDEM_PREFIX + txnId;
        RBucket<String> idemBucket = redissonClient.getBucket(idemKey, StringCodec.INSTANCE);
        long tIdem = System.nanoTime();
        boolean firstTime = idemBucket.setIfAbsent("1", Duration.ofDays(idempotencyTtlDays));
        recordPhase("idempotencyMs", tIdem);
        
        if (!firstTime) {
            long current = getCurrentPoints(customerId);
            return RewardResponse.builder()
                    .customerId(customerId)
                    .totalPoints(current)
                    .status("DUPLICATE_TRANSACTION")
                    .threadName(Thread.currentThread().toString())
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        }

        long pointsDelta = RewardUtils.calculatePoints(request.getAmount());

        long tPoints = System.nanoTime();
        redissonClient.getAtomicLong(EXPECTED_PREFIX + customerId).addAndGet(pointsDelta);
        long newPoints = ((Number) redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE)
                .addAndGet(customerId, pointsDelta)).longValue();
        recordPhase("pointsUpdateMs", tPoints);

        long tOutbox = System.nanoTime();
        String outboxJson = objectMapper.writeValueAsString(new RewardOutboxEvent(
                customerId,
                txnId,
                request.getAmount(),
                pointsDelta,
                OffsetDateTime.now()
        ));
        redissonClient.getDeque(OUTBOX_KEY, StringCodec.INSTANCE).addFirst(outboxJson);
        recordPhase("outboxMs", tOutbox);
        recordPhase("totalMs", t0);

        return RewardResponse.builder()
                .customerId(customerId)
                .totalPoints(newPoints)
                .status("SUCCESS")
                .threadName(Thread.currentThread().toString())
                .processingTimeMs(System.currentTimeMillis() - start)
                .build();
    }

    private void recordPhase(String phase, long startNanos) {
        if (RewardPhaseTiming.isActive()) {
            RewardPhaseTiming.record(phase, System.nanoTime() - startNanos);
        }
    }

    private long getCurrentPoints(String customerId) {
        try {
            Object raw = redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE).get(customerId);
            if (raw == null) {
                return 0L;
            }
            return Long.parseLong(raw.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
}
