package turbo.pos.boost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import turbo.pos.boost.exception.RedisUnavailableException;
import turbo.pos.boost.util.RewardUtils;
import turbo.pos.boost.util.RedisUtils;

/**
 * Redis path: Redisson lock theo customer, cộng điểm cache + outbox; rewards-batch ghi MySQL.
 * Redis lỗi → {@link RedisUnavailableException} → fallback MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rewards.mode", havingValue = "redis", matchIfMissing = true)
public class LockingRedisRewardService {

    private static final String HASH_KEY = "customer:points";
    private static final String OUTBOX_KEY = "rewards:outbox";
    private static final String EXPECTED_PREFIX = "expected:";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    /** Thời gian chờ lock (p2 hotspot cần đủ lớn để tránh LOCK_FAILED giả). */
    @org.springframework.beans.factory.annotation.Value("${app.rewards.lock.wait-seconds:30}")
    private long lockWaitSeconds;

    @org.springframework.beans.factory.annotation.Value("${app.rewards.lock.lease-seconds:30}")
    private long lockLeaseSeconds;

    public RewardResponse processReward(TransactionRequest request) {
        long start = System.currentTimeMillis();
        String customerId = request.getCustomerId();
        String txnId = request.getTransactionId();

        String lockKey = "reward:lock:" + customerId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                return RewardResponse.builder()
                        .customerId(customerId)
                        .totalPoints(0L)
                        .status("LOCK_FAILED")
                        .threadName(Thread.currentThread().toString())
                        .processingTimeMs(System.currentTimeMillis() - start)
                        .build();
            }

            // simulate IO (DB/network)
            // TimeUnit.MILLISECONDS.sleep(50);

            /*
            // Idempotency quick-check in Redis (DB uniqueness is enforced by batch module)
            String idemKey = "idempotency:" + txnId;
            Boolean firstTime = redis.opsForValue()
                    .setIfAbsent(idemKey, "1", java.time.Duration.ofSeconds(60));
            if (Boolean.FALSE.equals(firstTime)) {
                Long current = getCurrentPoints(customerId);
                return RewardResponse.builder()
                        .customerId(customerId)
                        .totalPoints(current == null ? 0L : current)
                        .status("DUPLICATE_TRANSACTION")
                        .threadName(Thread.currentThread().toString())
                        .processingTimeMs(System.currentTimeMillis() - start)
                        .build();
            }
            */

            long pointsDelta = RewardUtils.calculatePoints(request.getAmount());

            // SUCCESS: bump expected (DUPLICATE không bump).
            redissonClient.getAtomicLong(EXPECTED_PREFIX + customerId).addAndGet(pointsDelta);

            // Fast temporary update (read path)
            long newPoints = ((Number) redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE)
                    .addAndGet(customerId, pointsDelta)).longValue();
            // Outbox → rewards-batch → MySQL
            String outboxJson = objectMapper.writeValueAsString(new RewardOutboxEvent(
                    customerId,
                    txnId,
                    request.getAmount(),
                    pointsDelta,
                    OffsetDateTime.now()
            ));
            redissonClient.getDeque(OUTBOX_KEY, StringCodec.INSTANCE).addFirst(outboxJson);

            return RewardResponse.builder()
                    .customerId(customerId)
                    .totalPoints(newPoints)
                    .status("SUCCESS")
                    .threadName(Thread.currentThread().toString())
                    .processingTimeMs(System.currentTimeMillis() - start)
                    .build();
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
                // Lock đã hết hạn (lease expired) hoặc đã bị giải phóng, bỏ qua lỗi này
                log.warn("Lock for customer {} already released or expired: {}", customerId, e.getMessage());
            } catch (Exception e) {
                log.error("Error releasing lock for customer {}", customerId, e);
            }
        }
    }

    private Long getCurrentPoints(String customerId) {
        try {
            Object raw = redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE).get(customerId);
            if (raw == null) return 0L;
            return Long.parseLong(raw.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    public record RewardOutboxEvent(
            String customerId,
            String transactionId,
            double amount,
            long pointsDelta,
            OffsetDateTime createdAt
    ) {
    }
}
