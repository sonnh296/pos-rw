package turbo.pos.boost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
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
import turbo.pos.boost.redis.RewardCheckoutLuaExecutor;
import turbo.pos.boost.redis.RewardCheckoutLuaExecutor.LuaCheckoutResult;
import turbo.pos.boost.util.RedisUtils;
import turbo.pos.boost.util.RewardUtils;
import turbo.pos.common.RewardOutboxEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Redis path: Redisson lock theo customer, idempotency, cộng điểm cache + outbox; rewards-batch ghi MySQL.
 * Redis lỗi → {@link RedisUnavailableException} → fallback MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rewards.mode", havingValue = "redis", matchIfMissing = true)
public class LockingRedisRewardService {

    private static final String HASH_KEY = RewardCheckoutLuaExecutor.HASH_KEY;
    private static final String OUTBOX_KEY = RewardCheckoutLuaExecutor.OUTBOX_KEY;
    private static final String EXPECTED_PREFIX = RewardCheckoutLuaExecutor.EXPECTED_PREFIX;
    private static final String IDEM_PREFIX = RewardCheckoutLuaExecutor.IDEM_PREFIX;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final RewardCheckoutLuaExecutor rewardCheckoutLuaExecutor;

    @Value("${app.rewards.lock.wait-seconds:30}")
    private long lockWaitSeconds;

    @Value("${app.rewards.lock.lease-seconds:30}")
    private long lockLeaseSeconds;

    @Value("${app.rewards.idempotency.ttl-days:7}")
    private int idempotencyTtlDays;

    @Value("${app.rewards.stub-redis:false}")
    private boolean stubRedis;

    @Value("${app.rewards.use-lua:true}")
    private boolean useLua;

    @PostConstruct
    void logModes() {
        if (stubRedis) {
            log.warn("STUB REDIS ENABLED — LockingRedisRewardService skips Redisson (benchmark only)");
        } else if (useLua) {
            log.info("Reward checkout uses Lua script (idem + points + outbox in one round-trip after lock)");
        }
    }

    public RewardResponse processReward(TransactionRequest request) {
        long start = System.currentTimeMillis();
        long t0 = System.nanoTime();
        if (stubRedis) {
            return stubProcessReward(request, start, t0);
        }
        String customerId = request.getCustomerId();
        String txnId = request.getTransactionId();

        String lockKey = "reward:lock:" + customerId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            long tLockStart = System.nanoTime();
            acquired = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);
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

            if (useLua) {
                return processRewardWithLua(request, customerId, txnId, start, t0);
            }
            return processRewardMultiCall(request, customerId, txnId, start, t0);
        } catch (Exception e) {
            if (RedisUtils.isRedisUnavailable(e)) {
                log.error("LockingRedisRewardService: Redis unavailable -> circuit breaker fallback", e);
                throw new RedisUnavailableException("Redis unavailable", e);
            }
            log.error("LockingRedisRewardService.processReward failed", e);
            return RewardResponse.builder()
                    .customerId(customerId)
                    .totalPoints(0L)
                    .status("ERROR")
                    .threadName(Thread.currentThread().toString())
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
        } finally {
            try {
                if (acquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (IllegalMonitorStateException e) {
                log.warn("Lock for customer {} already released or expired: {}", customerId, e.getMessage());
            } catch (Exception e) {
                log.error("Error releasing lock for customer {}", customerId, e);
            }
        }
    }

    private RewardResponse processRewardWithLua(
            TransactionRequest request,
            String customerId,
            String txnId,
            long start,
            long t0) throws Exception {
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
            long t0) throws Exception {
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

    /** Benchmark-only: không Redis — cô lập overhead thread pool vs virtual. */
    private RewardResponse stubProcessReward(TransactionRequest request, long start, long t0) {
        long pointsDelta = RewardUtils.calculatePoints(request.getAmount());
        recordPhase("totalMs", t0);
        return RewardResponse.builder()
                .customerId(request.getCustomerId())
                .totalPoints(pointsDelta)
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
