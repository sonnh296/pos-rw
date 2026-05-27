package turbo.pos.boost.service;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import turbo.pos.boost.config.NonBlockingConcurrencyLimiter;
import turbo.pos.boost.config.RewardModeProperties;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.exception.AppException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import lombok.extern.slf4j.Slf4j;

/**
 * Facade: {@code redis} → {@link LockingRedisRewardService}; {@code mysql-only} → {@link MysqlLockingRewardService}.
 */
@Service
@Slf4j
public class LockingRewardService {

	private final RewardModeProperties rewardModeProperties;
	private final ObjectProvider<LockingRedisRewardService> redisLocking;
	private final ObjectProvider<MysqlLockingRewardService> mysqlLocking;
	private final NonBlockingConcurrencyLimiter concurrencyLimiter;
	private final RedissonClient redissonClient;

	@Value("${app.concurrency.enabled:true}")
	private boolean concurrencyLimitEnabled;

	public LockingRewardService(RewardModeProperties rewardModeProperties,
			ObjectProvider<LockingRedisRewardService> redisLocking,
			ObjectProvider<MysqlLockingRewardService> mysqlLocking,
			NonBlockingConcurrencyLimiter concurrencyLimiter,
			RedissonClient redissonClient) {
		this.rewardModeProperties = rewardModeProperties;
		this.redisLocking = redisLocking;
		this.mysqlLocking = mysqlLocking;
		this.concurrencyLimiter = concurrencyLimiter;
		this.redissonClient = redissonClient;
	}

	@CircuitBreaker(name = "redisLocking", fallbackMethod = "fallbackProcessReward")
	public RewardResponse processReward(TransactionRequest request) {
		if (concurrencyLimitEnabled) {
			return processWithConcurrencyLimit(request);
		}
		return processInternal(request);
	}

	/**
	 * Fail-fast khi đạt giới hạn concurrency (HTTP 429).
	 */
	private RewardResponse processWithConcurrencyLimit(TransactionRequest request) {
		boolean acquired = concurrencyLimiter.tryAcquire();

		if (!acquired) {
			log.warn("Concurrency limit reached for customer {}. Used: {}/{}",
				request.getCustomerId(),
				concurrencyLimiter.getUsedPermits(),
				concurrencyLimiter.getMaxConcurrent());

			return RewardResponse.builder()
				.customerId(request.getCustomerId())
				.totalPoints(0L)
				.status("TOO_MANY_REQUESTS")
				.threadName(Thread.currentThread().toString())
				.processingTimeMs(0L)
				.build();
		}

		try {
			return processInternal(request);
		} finally {
			concurrencyLimiter.release();
		}
	}

	private RewardResponse processInternal(TransactionRequest request) {
		if (rewardModeProperties.isMysqlOnly()) {
			return mysqlLocking.getObject().processReward(request);
		}
		return redisLocking.getObject().processReward(request);
	}

	/**
	 * Fallback to MySQL when Redis circuit breaker opens. Invalidates Redis cache after fallback.
	 */
	private RewardResponse fallbackProcessReward(TransactionRequest request, Throwable t) {
		log.warn("CircuitBreaker fallback (redisLock) => mysql-locking. cause={}", t == null ? "unknown" : t.toString());

		RewardResponse res = mysqlLocking.getObject().processReward(request);
		
		if (res != null) {
			res.setDegraded(true);
			res.setFallbackSource("mysql-locking");
			
			try {
				String cacheKey = "customer:points";
				redissonClient.getMap(cacheKey, StringCodec.INSTANCE).remove(request.getCustomerId());
				log.debug("Invalidated Redis cache for customer {} after fallback", request.getCustomerId());
			} catch (Exception e) {
				log.debug("Could not invalidate cache (Redis unavailable): {}", e.getMessage());
			}
		}
		
		return res;
	}

	/**
	 * Get current concurrency metrics (for monitoring)
	 */
	public int getAvailablePermits() {
		return concurrencyLimiter.getAvailablePermits();
	}

	public int getQueuedThreads() {
		return concurrencyLimiter.getQueueLength(); // always 0 — instant reject, no queue
	}
}
