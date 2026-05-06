package turbo.pos.boost.service;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

/** No-lock Redis path: demo race / lost update (không MySQL, không outbox). */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rewards.mode", havingValue = "redis", matchIfMissing = true)
public class NoLockRedisRewardService {

	private static final String HASH_KEY = "customer:points";
	private static final String EXPECTED_PREFIX = "expected:";

	private final StringRedisTemplate stringRedisTemplate;

	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		try {
			String customerId = request.getCustomerId();
			long pointsToAdd = Math.round(request.getAmount() * 10);

			// Mỗi SUCCESS tăng giá trị expected (độc lập với race condition của RMW).
			stringRedisTemplate.opsForValue().increment(EXPECTED_PREFIX + customerId, pointsToAdd);

			TimeUnit.MILLISECONDS.sleep(50);

			// Cố tình thực hiện read-modify-write không atomic để minh họa hiện tượng race condition khi không dùng lock.
			Object raw = stringRedisTemplate.opsForHash().get(HASH_KEY, customerId);
			long current = raw == null ? 0L : Long.parseLong(raw.toString());
			long newPoints = current + pointsToAdd;
			stringRedisTemplate.opsForHash().put(HASH_KEY, customerId, Long.toString(newPoints));

			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(newPoints)
					.status("SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (Exception e) {
			if (isRedisUnavailable(e)) {
				log.error("NoLockRedisRewardService: Redis không khả dụng -> kích hoạt circuit breaker", e);
				throw new RedisUnavailableException("Redis unavailable", e);
			}
			log.error("NoLockRedisRewardService thất bại", e);
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

			// Kiểm tra các lỗi kết nối hoặc timeout (Spring Redis / Redisson / Netty...)
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
