package turbo.pos.boost.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import turbo.pos.boost.config.RewardModeProperties;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

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

	public LockingRewardService(RewardModeProperties rewardModeProperties,
			ObjectProvider<LockingRedisRewardService> redisLocking,
			ObjectProvider<MysqlLockingRewardService> mysqlLocking) {
		this.rewardModeProperties = rewardModeProperties;
		this.redisLocking = redisLocking;
		this.mysqlLocking = mysqlLocking;
	}

	@CircuitBreaker(name = "redisLocking", fallbackMethod = "fallbackProcessReward")
	public RewardResponse processReward(TransactionRequest request) {
		if (rewardModeProperties.isMysqlOnly()) {
			return mysqlLocking.getObject().processReward(request);
		}
		return redisLocking.getObject().processReward(request);
	}

	private RewardResponse fallbackProcessReward(TransactionRequest request, Throwable t) {
		// Redis gặp sự cố => Fallback sang cơ chế lock của MySQL để đảm bảo tính nhất quán.
		log.warn("CircuitBreaker fallback (redisLock) => mysql-locking. cause={}", t == null ? "unknown" : t.toString());
		RewardResponse res = mysqlLocking.getObject().processReward(request);
		if (res != null && res.getStatus() != null) {
			res.setStatus(res.getStatus() + "_REDIS_LOCK_REJECTED_FALLBACK_MYSQL");
		}
		return res;
	}
}
