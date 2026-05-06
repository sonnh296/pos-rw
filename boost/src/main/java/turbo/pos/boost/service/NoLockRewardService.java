package turbo.pos.boost.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import turbo.pos.boost.config.RewardModeProperties;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Service
@Slf4j
public class NoLockRewardService {

	private final RewardModeProperties rewardModeProperties;
	private final ObjectProvider<NoLockRedisRewardService> redisNoLock;
	private final ObjectProvider<MysqlNoLockRewardService> mysqlNoLock;

	public NoLockRewardService(RewardModeProperties rewardModeProperties,
			ObjectProvider<NoLockRedisRewardService> redisNoLock,
			ObjectProvider<MysqlNoLockRewardService> mysqlNoLock) {
		this.rewardModeProperties = rewardModeProperties;
		this.redisNoLock = redisNoLock;
		this.mysqlNoLock = mysqlNoLock;
	}

	@CircuitBreaker(name = "redisNoLock", fallbackMethod = "fallbackProcessReward")
	public RewardResponse processReward(TransactionRequest request) {
		if (rewardModeProperties.isMysqlOnly()) {
			return mysqlNoLock.getObject().processReward(request);
		}
		return redisNoLock.getObject().processReward(request);
	}

	private RewardResponse fallbackProcessReward(TransactionRequest request, Throwable t) {
		// Redis gặp sự cố => Chuyển sang chế độ dự phòng: dùng trực tiếp MySQL để đảm bảo tính sẵn sàng.
		log.warn("CircuitBreaker fallback (redisNoLock) => mysql-only. cause={}", t == null ? "unknown" : t.toString());
		RewardResponse res = mysqlNoLock.getObject().processReward(request);
		if (res != null && res.getStatus() != null) {
			res.setStatus(res.getStatus() + "_REDIS_FALLBACK_MYSQL");
		}
		return res;
	}
}
