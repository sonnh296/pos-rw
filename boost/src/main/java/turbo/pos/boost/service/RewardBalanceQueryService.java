package turbo.pos.boost.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.sql.ResultSet;
import java.sql.SQLException;

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
	private static final String OUTBOX_KEY = "rewards:outbox";
	private static final String IDEM_PREFIX = "idempotency:*";
	private static final String EXPECTED_PREFIX = "expected:";
	private static final String EXPECTED_SCAN_PATTERN = "expected:*";

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
			return null;
		}
	}

	/**
	 * Tổng điểm đã quyết định áp dụng (Redis {@code expected:{customerId}}, INCRBY trên SUCCESS).
	 * So với {@link #getRedisPointsOrNull(String)} để phát hiện lost update. {@code null} nếu Redis lỗi.
	 */
	public Long getExpectedPointsOrNull(String customerId) {
		StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
		if (redis == null) {
			return null;
		}
		try {
			String raw = redis.opsForValue().get(EXPECTED_PREFIX + customerId);
			return raw == null ? 0L : Long.parseLong(raw);
		} catch (Exception e) {
			return null;
		}
	}

	/** Expected từ ledger MySQL (Redis down hoặc mysql-only). Với Redis+outbox, ledger có thể chậm hơn cache. */
	public long getExpectedPointsFromMysqlLedger(String customerId) {
		return jdbcClient.sql("SELECT COALESCE(SUM(points_delta), 0) FROM reward_ledger WHERE customer_id = ?")
				.param(customerId)
				.query(Long.class)
				.optional()
				.orElse(0L);
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

	/** Σ MySQL {@code customer_balance} vs Σ Redis {@code customer:points}; {@code outboxPending} khi chưa drain. */
	public Map<String, Object> globalConsistencyReport() {
		long mysqlTotal = jdbcClient.sql("SELECT COALESCE(SUM(balance), 0) FROM customer_balance")
				.query(Long.class)
				.optional()
				.orElse(0L);
		long mysqlCustomers = jdbcClient.sql("SELECT COUNT(*) FROM customer_balance")
				.query(Long.class)
				.optional()
				.orElse(0L);

		Long redisTotal = null;
		Long redisCustomers = null;
		Long outboxPending = null;
		Long expectedTotal = null;
		StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
		if (redis != null) {
			try {
				Map<Object, Object> all = redis.opsForHash().entries(HASH_KEY);
				long sum = 0L;
				for (Object v : all.values()) {
					sum += asLong(v);
				}
				redisTotal = sum;
				redisCustomers = (long) all.size();
				outboxPending = redis.opsForList().size(OUTBOX_KEY);
				expectedTotal = sumExpectedKeys(redis);
			} catch (Exception ignored) {
			}
		}

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("mysqlTotalPoints", mysqlTotal);
		out.put("mysqlCustomerCount", mysqlCustomers);
		out.put("redisTotalPoints", redisTotal);
		out.put("redisCustomerCount", redisCustomers);
		out.put("outboxPending", outboxPending);
		out.put("expectedTotalPoints", expectedTotal);

		// Verdict: ưu tiên OUTBOX_DRAINING trước khi so sánh tổng redis/mysql (outbox > 0 ⇒ redis có thể > mysql hợp lệ).
		String verdict;
		if (redisTotal == null) {
			verdict = "REDIS_UNAVAILABLE";
		} else if (outboxPending != null && outboxPending > 0) {
			verdict = "OUTBOX_DRAINING";
		} else if (redisTotal == mysqlTotal) {
			verdict = "CONSISTENT";
		} else if (redisTotal > mysqlTotal) {
			verdict = "MYSQL_BEHIND";
		} else {
			verdict = "REDIS_BEHIND";
		}
		out.put("verdict", verdict);
		out.put("diff", redisTotal == null ? null : (redisTotal - mysqlTotal));

		// Σ expected:* vs Σ customer:points (lệch → lost update ngoài lock).
		String promiseVerdict;
		Long promiseDiff;
		if (expectedTotal == null || redisTotal == null) {
			promiseVerdict = "REDIS_UNAVAILABLE";
			promiseDiff = null;
		} else {
			promiseDiff = expectedTotal - redisTotal;
			if (promiseDiff == 0L) {
				promiseVerdict = "PROMISE_KEPT";
			} else if (promiseDiff > 0L) {
				promiseVerdict = "LOST_UPDATE_DETECTED";
			} else {
				promiseVerdict = "OVER_APPLIED";
			}
		}
		out.put("promiseVerdict", promiseVerdict);
		out.put("promiseDiff", promiseDiff);
		out.put("note", "Chạy sau khi load test xong vài giây để rewards-batch drain outbox.");
		return out;
	}

	/** Σ giá trị key {@code expected:*} (SCAN/KEYS theo driver; demo-only). */
	private static Long sumExpectedKeys(StringRedisTemplate redis) {
		try {
			Set<String> keys = redis.keys(EXPECTED_SCAN_PATTERN);
			if (keys == null || keys.isEmpty()) return 0L;
			List<String> values = redis.opsForValue().multiGet(keys);
			if (values == null) return 0L;
			long sum = 0L;
			for (String v : values) {
				if (v == null) continue;
				try {
					sum += Long.parseLong(v);
				} catch (NumberFormatException ignored) {
					// skip malformed
				}
			}
			return sum;
		} catch (Exception e) {
			return null;
		}
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
				? "Không có Redis (mysql-only hoặc bean tắt)."
				: "inSync: Redis trùng customer_balance.");
		return out;
	}

	/**
	 * Danh sách khách hàng + điểm để hiển thị UI (có phân trang/tìm kiếm).
	 * Nguồn chính: {@code customer_balance}; nếu rỗng nhưng có Redis thì fallback hash {@code customer:points} (phân trang trong memory).
	 */
	public Map<String, Object> listCustomerPoints(int limit, int offset, String keyword) {
		limit = Math.min(Math.max(1, limit), 200);
		offset = Math.max(0, offset);
		String q = keyword == null ? "" : keyword.trim();
		boolean hasKeyword = !q.isEmpty();

		List<Map<String, Object>> mysqlRows;
		long total;

		if (hasKeyword) {
			String like = "%" + q + "%";
			mysqlRows = jdbcClient.sql("""
					SELECT customer_id, balance, updated_at
					FROM customer_balance
					WHERE customer_id LIKE ?
					ORDER BY updated_at DESC
					LIMIT ? OFFSET ?
					""")
					.params(like, limit, offset)
					.query((rs, rowNum) -> toBalanceRow(rs))
					.list();

			total = jdbcClient.sql("SELECT COUNT(*) FROM customer_balance WHERE customer_id LIKE ?")
					.param(like)
					.query(Long.class)
					.single();
		} else {
			mysqlRows = jdbcClient.sql("""
					SELECT customer_id, balance, updated_at
					FROM customer_balance
					ORDER BY updated_at DESC
					LIMIT ? OFFSET ?
					""")
					.params(limit, offset)
					.query((rs, rowNum) -> toBalanceRow(rs))
					.list();

			total = jdbcClient.sql("SELECT COUNT(*) FROM customer_balance")
					.query(Long.class)
					.single();
		}

		boolean mysqlOnly = rewardModeProperties.isMysqlOnly();
		List<Map<String, Object>> rows = new ArrayList<>(mysqlRows.size());
		for (Map<String, Object> mysqlRow : mysqlRows) {
			String customerId = String.valueOf(mysqlRow.get("customer_id"));
			long mysqlBalance = asLong(mysqlRow.get("balance"));
			Long redisPoints = mysqlOnly ? null : getRedisPointsOrNull(customerId);
			rows.add(buildPointsRow(customerId, mysqlBalance, redisPoints, mysqlOnly, mysqlRow.get("updated_at")));
		}

		// Redis-only demo: MySQL chưa có row.
		if (!mysqlOnly && rows.isEmpty()) {
			StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
			if (redis != null) {
				try {
					Map<Object, Object> redisAll = redis.opsForHash().entries(HASH_KEY);
					List<Map<String, Object>> redisRows = redisAll.entrySet().stream()
							.map(e -> Map.entry(String.valueOf(e.getKey()), asLong(e.getValue())))
							.filter(e -> !hasKeyword || e.getKey().contains(q))
							.sorted(Comparator.comparing(Map.Entry::getKey))
							.map(e -> buildPointsRow(e.getKey(), 0L, e.getValue(), false, null))
							.toList();

					// Redis hash không có LIMIT/OFFSET SQL — cắt subList sau khi đã build full list.
					int from = Math.min(offset, redisRows.size());
					int to = Math.min(from + limit, redisRows.size());
					rows = new ArrayList<>(redisRows.subList(from, to));
					total = redisRows.size();
				} catch (Exception ignored) {
				}
			}
		}

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("rows", rows);
		out.put("total", total);
		out.put("limit", limit);
		out.put("offset", offset);
		out.put("keyword", q);
		return out;
	}

	/** Xóa toàn bộ dữ liệu demo điểm để reset test. */
	public Map<String, Object> clearAllPointsData() {
		long deletedLedger = jdbcClient.sql("DELETE FROM reward_ledger").update();
		long deletedBalances = jdbcClient.sql("DELETE FROM customer_balance").update();

		long deletedRedisMain = 0L;
		long deletedRedisIdempotency = 0L;
		long deletedRedisOutbox = 0L;
		long deletedRedisExpected = 0L;
		boolean redisFlushed = false;
		String redisNote = "skipped";

		StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
		if (redis != null) {
			try {
				Boolean main = redis.delete(HASH_KEY);
				Boolean outbox = redis.delete(OUTBOX_KEY);
				deletedRedisMain = Boolean.TRUE.equals(main) ? 1L : 0L;
				deletedRedisOutbox = Boolean.TRUE.equals(outbox) ? 1L : 0L;

				Set<String> idemKeys = redis.keys(IDEM_PREFIX);
				if (idemKeys != null && !idemKeys.isEmpty()) {
					Long n = redis.delete(idemKeys);
					deletedRedisIdempotency = n == null ? 0L : n;
				}

				// Xoá expected:* (đếm riêng; FLUSHDB cũng dọn).
				Set<String> expectedKeys = redis.keys(EXPECTED_SCAN_PATTERN);
				if (expectedKeys != null && !expectedKeys.isEmpty()) {
					Long n = redis.delete(expectedKeys);
					deletedRedisExpected = n == null ? 0L : n;
				}

				// FLUSHDB: lock Redisson, key runtime, v.v.
				try {
					var factory = redis.getConnectionFactory();
					if (factory != null) {
						try (var conn = factory.getConnection()) {
							conn.serverCommands().flushDb();
							redisFlushed = true;
							redisNote = "flushed";
						}
					}
				} catch (Exception flushEx) {
					redisNote = "flush-failed: " + flushEx.getClass().getSimpleName();
				}
			} catch (Exception ignored) {
			}
		}

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("deletedLedgerRows", deletedLedger);
		out.put("deletedBalanceRows", deletedBalances);
		out.put("deletedRedisMainHash", deletedRedisMain);
		out.put("deletedRedisOutbox", deletedRedisOutbox);
		out.put("deletedRedisIdempotencyKeys", deletedRedisIdempotency);
		out.put("deletedRedisExpectedKeys", deletedRedisExpected);
		out.put("redisFlushed", redisFlushed);
		out.put("redisNote", redisNote);
		out.put("status", "CLEARED");
		return out;
	}

	/** Đồng bộ {@code customer:points} và {@code expected:} từ MySQL. No-op nếu mysql-only hoặc Redis tắt. */
	public Map<String, Object> rehydrateRedisFromMysql() {
		long started = System.currentTimeMillis();
		Map<String, Object> out = new LinkedHashMap<>();

		if (rewardModeProperties.isMysqlOnly()) {
			out.put("status", "SKIPPED");
			out.put("reason", "mysql-only mode");
			out.put("tookMs", System.currentTimeMillis() - started);
			return out;
		}

		StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
		if (redis == null) {
			out.put("status", "SKIPPED");
			out.put("reason", "redis bean unavailable");
			out.put("tookMs", System.currentTimeMillis() - started);
			return out;
		}

		long customers = 0L;
		try {
			List<Map<String, Object>> rows = jdbcClient.sql("""
					SELECT customer_id, balance
					FROM customer_balance
					""")
					.query((rs, rowNum) -> {
						Map<String, Object> r = new HashMap<>();
						r.put("customerId", rs.getString("customer_id"));
						r.put("balance", rs.getLong("balance"));
						return r;
					})
					.list();

			if (!rows.isEmpty()) {
				// HSET customer:points
				Map<String, String> hash = new HashMap<>(rows.size());
				for (Map<String, Object> r : rows) {
					String customerId = String.valueOf(r.get("customerId"));
					String balance = String.valueOf(asLong(r.get("balance")));
					hash.put(customerId, balance);
				}
				redis.opsForHash().putAll(HASH_KEY, hash);

				// Đồng bộ expected:{id} = balance để race-check / verdict không lệch sau khi Redis trống rồi rehydrate.
				for (Map<String, Object> r : rows) {
					String customerId = String.valueOf(r.get("customerId"));
					String balance = String.valueOf(asLong(r.get("balance")));
					redis.opsForValue().set(EXPECTED_PREFIX + customerId, balance);
				}
				customers = rows.size();
			}

			out.put("status", "REHYDRATED");
			out.put("customers", customers);
		} catch (Exception e) {
			out.put("status", "FAILED");
			out.put("error", e.getClass().getSimpleName());
			out.put("message", e.getMessage());
		}

		out.put("tookMs", System.currentTimeMillis() - started);
		return out;
	}

	private static long asLong(Object value) {
		if (value instanceof Number n) {
			return n.longValue();
		}
		if (value == null) {
			return 0L;
		}
		try {
			return Long.parseLong(value.toString());
		} catch (Exception ignored) {
			return 0L;
		}
	}

	private static Map<String, Object> toBalanceRow(ResultSet rs) throws SQLException {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("customer_id", rs.getString("customer_id"));
		row.put("balance", rs.getLong("balance"));
		row.put("updated_at", rs.getObject("updated_at"));
		return row;
	}

	private static Map<String, Object> buildPointsRow(String customerId, long mysqlBalance, Long redisPoints, boolean mysqlOnly,
			Object updatedAt) {
		// Chế độ redis: ưu tiên điểm hash; null (Redis lỗi/chưa có field) thì lấy mysql để UI không âm thầm 0.
		long primaryPoints = mysqlOnly ? mysqlBalance : (redisPoints != null ? redisPoints : mysqlBalance);
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("customerId", customerId);
		row.put("mysqlBalance", mysqlBalance);
		row.put("redisPoints", redisPoints);
		row.put("primaryPoints", primaryPoints);
		row.put("source", mysqlOnly ? "mysql" : "redis");
		row.put("inSync", redisPoints != null && redisPoints == mysqlBalance);
		row.put("updatedAt", updatedAt);
		return row;
	}
}
