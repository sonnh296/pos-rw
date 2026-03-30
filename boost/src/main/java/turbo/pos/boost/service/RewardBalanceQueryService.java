package turbo.pos.boost.service;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import turbo.pos.boost.config.RewardModeProperties;

@Service
@RequiredArgsConstructor
public class RewardBalanceQueryService {

	private static final String HASH_KEY = "customer:points";

	private final JdbcClient jdbcClient;
	private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;
	private final RewardModeProperties rewardModeProperties;

	public long getMysqlBalance(String customerId) {
		return jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ?")
				.param(customerId)
				.query(Long.class)
				.optional()
				.orElse(0L);
	}

	public Long getRedisPointsOrNull(String customerId) {
		StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
		if (redis == null) {
			return null;
		}
		try {
			Object raw = redis.opsForHash().get(HASH_KEY, customerId);
			if (raw == null) {
				return 0L;
			}
			return Long.parseLong(raw.toString());
		} catch (Exception e) {
			// If Redis is down mid-run, availability: do not fail whole endpoint.
			return null;
		}
	}

	/** Điểm “chính” theo chế độ: Redis (mặc định) hoặc MySQL (mysql-only). */
	public Map<String, Object> getPrimaryPoints(String customerId) {
		if (rewardModeProperties.isMysqlOnly()) {
			long p = getMysqlBalance(customerId);
			return Map.of("customerId", customerId, "points", p, "source", "mysql");
		}
		Long redis = getRedisPointsOrNull(customerId);
		return Map.of("customerId", customerId, "points", redis != null ? redis : 0L, "source", "redis");
	}

	/** So sánh consistency: MySQL vs Redis (Redis vắng khi profile mysql-only). */
	public Map<String, Object> compareBalances(String customerId) {
		long mysql = getMysqlBalance(customerId);
		Long redis = getRedisPointsOrNull(customerId);
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("customerId", customerId);
		out.put("mysqlBalance", mysql);
		out.put("redisPoints", redis);
		out.put("inSync", redis != null && mysql == redis);
		out.put("note", redis == null
				? "Redis không dùng (profile mysql-only hoặc chưa cấu hình)."
				: "inSync=true khi điểm Redis trùng customer_balance MySQL (sau các giao dịch /lock redis).");
		return out;
	}
}
