package turbo.pos.boost.service;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.exception.RedisUnavailableException;
import turbo.pos.boost.util.RewardUtils;
import turbo.pos.boost.util.RedisUtils;

/** No-lock Redis path: demo race / lost update (không MySQL, không outbox). */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rewards.mode", havingValue = "redis", matchIfMissing = true)
public class NoLockRedisRewardService {

	private static final String HASH_KEY = "customer:points";
	private static final String EXPECTED_PREFIX = "expected:";

	private final RedissonClient redissonClient;

	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		try {
			String customerId = request.getCustomerId();
			long pointsToAdd = RewardUtils.calculatePoints(request.getAmount());

			// Mỗi SUCCESS tăng giá trị expected (độc lập với race condition của RMW).
			redissonClient.getAtomicLong(EXPECTED_PREFIX + customerId).addAndGet(pointsToAdd);
			
			TimeUnit.MILLISECONDS.sleep(50);

			Object raw = redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE).get(customerId);
			long current = raw == null ? 0L : Long.parseLong(raw.toString());
			long newPoints = current + pointsToAdd;
			redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE).put(customerId, Long.toString(newPoints));

			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(newPoints)
					.status("SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (Exception e) {
			if (RedisUtils.isRedisUnavailable(e)) {
				log.error("NoLockRedisRewardService: Redis unavailable -> triggering circuit breaker", e);
				throw new RedisUnavailableException("Redis unavailable", e);
			}
			log.error("NoLockRedisRewardService failed", e);
			return RewardResponse.builder()
					.customerId(request.getCustomerId())
					.totalPoints(0L)
					.status("ERROR")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		}
	}


}
