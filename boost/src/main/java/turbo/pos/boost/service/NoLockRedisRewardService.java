package turbo.pos.boost.service;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

/**
 * Không lock (race demo).
 * - Chỉ cộng điểm tạm trên Redis (không ghi MySQL, không outbox).
 * - Dùng để chứng minh lost update khi concurrent cao.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rewards.mode", havingValue = "redis", matchIfMissing = true)
public class NoLockRedisRewardService {

	private static final String HASH_KEY = "customer:points";

	private final StringRedisTemplate stringRedisTemplate;

	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		try {
			String customerId = request.getCustomerId();
			TimeUnit.MILLISECONDS.sleep(50);

			long pointsToAdd = Math.round(request.getAmount() * 10);
			Long newPoints = stringRedisTemplate.opsForHash().increment(HASH_KEY, customerId, pointsToAdd);
			if (newPoints == null) {
				newPoints = 0L;
			}

			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(newPoints)
					.status("SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("NoLockRedisRewardService interrupted", e);
			return RewardResponse.builder()
					.customerId(request.getCustomerId())
					.totalPoints(0L)
					.status("ERROR")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (Exception e) {
			if (isRedisUnavailable(e)) {
				log.error("NoLockRedisRewardService: Redis unavailable -> circuit breaker fallback", e);
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

	private static boolean isRedisUnavailable(Throwable e) {
		Throwable t = e;
		while (t != null) {
			String cn = t.getClass().getName();
			String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();

			// Heuristic: connection/timeout failures (Spring Redis / Redisson / netty / etc.)
			if (cn.contains("RedisConnection") || cn.contains("RedisTimeout") || cn.contains("Redisson")
					|| t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException) {
				return true;
			}
			if (msg.contains("connection") || msg.contains("refused") || msg.contains("timeout")) {
				return true;
			}
			t = t.getCause();
		}
		return false;
	}
}
