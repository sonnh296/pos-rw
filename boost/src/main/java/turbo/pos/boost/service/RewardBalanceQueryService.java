package turbo.pos.boost.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import turbo.pos.boost.repository.RewardBalanceQueryRepository;
import turbo.pos.boost.config.RewardModeProperties;
import turbo.pos.boost.dto.ConsistencyReportResponse;
import turbo.pos.boost.dto.CustomerPointsResponse;
import java.util.HashSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardBalanceQueryService {

	private static final String HASH_KEY = "customer:points";
	private static final String OUTBOX_KEY = "rewards:outbox";
	private static final String IDEM_PREFIX = "idempotency:*";
	private static final String EXPECTED_PREFIX = "expected:";
	private static final String EXPECTED_SCAN_PATTERN = "expected:*";

	private final RewardBalanceQueryRepository queryRepository;
	private final RedissonClient redissonClient;
	private final RewardModeProperties rewardModeProperties;

	public long getMysqlBalance(String customerId) {
		return queryRepository.getMysqlBalance(customerId);
	}

	public Long getRedisPointsOrNull(String customerId) {
		try {
			Object raw = redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE).get(customerId);
			if (raw == null) {
				return 0L;
			}
			return Long.parseLong(raw.toString());
		} catch (Exception e) {
			log.warn("Could not fetch Redis points for customer {}: {}", customerId, e.getMessage());
			return null;
		}
	}

	/**
	 * Tổng điểm đã quyết định áp dụng (Redis {@code expected:{customerId}}, INCRBY trên SUCCESS).
	 * So với {@link #getRedisPointsOrNull(String)} để phát hiện lost update. {@code null} nếu Redis lỗi.
	 */
	public Long getExpectedPointsOrNull(String customerId) {
		try {
			String raw = (String) redissonClient.getBucket(EXPECTED_PREFIX + customerId, StringCodec.INSTANCE).get();
			return raw == null ? 0L : Long.parseLong(raw);
		} catch (Exception e) {
			log.warn("Could not fetch Expected points for customer {}: {}", customerId, e.getMessage());
			return null;
		}
	}

	/** Expected từ ledger MySQL (Redis down hoặc mysql-only). Với Redis+outbox, ledger có thể chậm hơn cache. */
	public long getExpectedPointsFromMysqlLedger(String customerId) {
		return queryRepository.getExpectedPointsFromMysqlLedger(customerId);
	}

	/** Điểm “chính” theo chế độ: Redis (mặc định) hoặc MySQL (mysql-only). */
	public CustomerPointsResponse getPrimaryPoints(String customerId) {
		if (rewardModeProperties.isMysqlOnly()) {
			long p = getMysqlBalance(customerId);
			return CustomerPointsResponse.builder()
					.customerId(customerId)
					.primaryPoints(p)
					.source("mysql")
					.build();
		}
		Long redis = getRedisPointsOrNull(customerId);
		return CustomerPointsResponse.builder()
				.customerId(customerId)
				.primaryPoints(redis != null ? redis : 0L)
				.source("redis")
				.build();
	}

	/** Tong MySQL {@code customer_balance} vs Tong Redis {@code customer:points}; {@code outboxPending} khi chưa drain. */
	public ConsistencyReportResponse globalConsistencyReport() {
		long mysqlTotal = queryRepository.getTotalMysqlBalance();
		long mysqlCustomers = queryRepository.getTotalMysqlCustomers();

		Long redisTotal = null;
		Long redisCustomers = null;
		Long outboxPending = null;
		Long expectedTotal = null;
		try {
			Map<Object, Object> all = redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE);
			long sum = 0L;
			for (Object v : all.values()) {
				sum += asLong(v);
			}
			redisTotal = sum;
			redisCustomers = (long) all.size();
			outboxPending = (long) redissonClient.getDeque(OUTBOX_KEY, StringCodec.INSTANCE).size();
			expectedTotal = sumExpectedKeys();
		} catch (Exception e) {
			log.error("Error generating global consistency report: ", e);
		}

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

		// Tong expected:* vs Tong customer:points (lệch → lost update ngoài lock).
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

		return ConsistencyReportResponse.builder()
				.mysqlTotalPoints(mysqlTotal)
				.mysqlCustomerCount(mysqlCustomers)
				.redisTotalPoints(redisTotal)
				.redisCustomerCount(redisCustomers)
				.outboxPending(outboxPending)
				.expectedTotalPoints(expectedTotal)
				.verdict(verdict)
				.diff(redisTotal == null ? null : (redisTotal - mysqlTotal))
				.promiseVerdict(promiseVerdict)
				.promiseDiff(promiseDiff)
				.note("Chạy sau khi load test xong vài giây để rewards-batch drain outbox.")
				.build();
	}

	/** Tong giá trị key {@code expected:*} (Sử dụng SCAN để tránh chặn Redis). */
	private Long sumExpectedKeys() {
		try {
			long sum = 0L;
			Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(EXPECTED_SCAN_PATTERN);
			for (String key : keys) {
				String v = (String) redissonClient.getBucket(key, StringCodec.INSTANCE).get();
				if (v != null) {
					try {
						sum += Long.parseLong(v);
					} catch (NumberFormatException e) {
						log.debug("Skipping malformed expected key: {}", v);
					}
				}
			}
			return sum;
		} catch (Exception e) {
			log.error("Error summing Expected points from Redis: ", e);
			return null;
		}
	}

	/** So sánh consistency: MySQL vs Redis (Redis vắng khi profile mysql-only). */
	public CustomerPointsResponse compareBalances(String customerId) {
		long mysql = getMysqlBalance(customerId);
		Long redis = getRedisPointsOrNull(customerId);
		return CustomerPointsResponse.builder()
				.customerId(customerId)
				.mysqlBalance(mysql)
				.redisPoints(redis)
				.inSync(redis != null && mysql == redis)
				.build();
	}

	/**
	 * Danh sách khách hàng + điểm để hiển thị UI (có phân trang/tìm kiếm).
	 */
	public CustomerPointsResponse.PagedList listCustomerPoints(int limit, int offset, String keyword) {
		limit = Math.min(Math.max(1, limit), 200);
		offset = Math.max(0, offset);
		String q = keyword == null ? "" : keyword.trim();
		boolean hasKeyword = !q.isEmpty();

		List<Map<String, Object>> mysqlRows;
		long total;

		if (hasKeyword) {
			String like = "%" + q + "%";
			mysqlRows = queryRepository.searchCustomerBalances(like, limit, offset);
			total = queryRepository.countCustomerBalancesByKeyword(like);
		} else {
			mysqlRows = queryRepository.getCustomerBalances(limit, offset);
			total = queryRepository.countAllCustomerBalances();
		}

		boolean mysqlOnly = rewardModeProperties.isMysqlOnly();
		List<CustomerPointsResponse> rows = new ArrayList<>(mysqlRows.size());
		for (Map<String, Object> mysqlRow : mysqlRows) {
			String customerId = String.valueOf(mysqlRow.get("customer_id"));
			long mysqlBalance = asLong(mysqlRow.get("balance"));
			Long redisPoints = mysqlOnly ? null : getRedisPointsOrNull(customerId);
			rows.add(buildPointsRowDto(customerId, mysqlBalance, redisPoints, mysqlOnly, mysqlRow.get("updated_at")));
		}

		// Redis-only demo: MySQL chưa có row.
		if (!mysqlOnly && rows.isEmpty()) {
			try {
				Map<Object, Object> redisAll = redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE);
				List<CustomerPointsResponse> redisRows = redisAll.entrySet().stream()
						.map(e -> Map.entry(String.valueOf(e.getKey()), asLong(e.getValue())))
						.filter(e -> !hasKeyword || e.getKey().contains(q))
						.sorted(Comparator.comparing(Map.Entry::getKey))
						.map(e -> buildPointsRowDto(e.getKey(), 0L, e.getValue(), false, null))
						.collect(Collectors.toList());

				int from = Math.min(offset, redisRows.size());
				int to = Math.min(from + limit, redisRows.size());
				rows = new ArrayList<>(redisRows.subList(from, to));
				total = redisRows.size();
			} catch (Exception e) {
				log.warn("Could not fetch customer list from Redis fallback: {}", e.getMessage());
			}
		}

		return CustomerPointsResponse.PagedList.builder()
				.rows(rows)
				.total(total)
				.limit(limit)
				.offset(offset)
				.keyword(q)
				.build();
	}

	/** Xóa toàn bộ dữ liệu demo điểm để reset test. */
	public Map<String, Object> clearAllPointsData() {
		long deletedLedger = queryRepository.deleteAllRewardLedger();
		long deletedBalances = queryRepository.deleteAllCustomerBalance();

		long deletedRedisMain = 0L;
		long deletedRedisIdempotency = 0L;
		long deletedRedisOutbox = 0L;
		long deletedRedisExpected = 0L;
		boolean redisFlushed = false;
		String redisNote = "flushed";

		try {
			deletedRedisMain = redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE).delete() ? 1L : 0L;
			deletedRedisOutbox = redissonClient.getDeque(OUTBOX_KEY, StringCodec.INSTANCE).delete() ? 1L : 0L;

			deletedRedisIdempotency = redissonClient.getKeys().deleteByPattern(IDEM_PREFIX);
			deletedRedisExpected = redissonClient.getKeys().deleteByPattern(EXPECTED_SCAN_PATTERN);

			redissonClient.getKeys().flushdb();
			redisFlushed = true;
		} catch (Exception e) {
			log.error("Error clearing Redis data: ", e);
			redisNote = "failed: " + e.getMessage();
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

		long customers = 0L;
		try {
			List<Map<String, Object>> rows = queryRepository.getAllCustomerBalances();

			if (!rows.isEmpty()) {
				// HSET customer:points
				Map<String, String> hash = new HashMap<>(rows.size());
				for (Map<String, Object> r : rows) {
					String customerId = String.valueOf(r.get("customerId"));
					String balance = String.valueOf(asLong(r.get("balance")));
					hash.put(customerId, balance);
				}
				redissonClient.getMap(HASH_KEY, StringCodec.INSTANCE).putAll(hash);

				// Đồng bộ expected:{id} = balance để race-check / verdict không lệch sau khi Redis trống rồi rehydrate.
				for (Map<String, Object> r : rows) {
					String customerId = String.valueOf(r.get("customerId"));
					String balance = String.valueOf(asLong(r.get("balance")));
					redissonClient.getBucket(EXPECTED_PREFIX + customerId, StringCodec.INSTANCE).set(balance);
				}
				customers = rows.size();
			}

			out.put("status", "REHYDRATED");
			out.put("customers", customers);
		} catch (Exception e) {
			log.error("Rehydrate Redis failed: ", e);
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
		} catch (Exception e) {
			log.debug("Could not parse value as Long: {}", value);
			return 0L;
		}
	}

	private static CustomerPointsResponse buildPointsRowDto(String customerId, long mysqlBalance, Long redisPoints, boolean mysqlOnly,
			Object updatedAt) {
		long primaryPoints = mysqlOnly ? mysqlBalance : (redisPoints != null ? redisPoints : mysqlBalance);
		return CustomerPointsResponse.builder()
				.customerId(customerId)
				.mysqlBalance(mysqlBalance)
				.redisPoints(redisPoints)
				.primaryPoints(primaryPoints)
				.source(mysqlOnly ? "mysql" : "redis")
				.inSync(redisPoints != null && redisPoints == mysqlBalance)
				.updatedAt(updatedAt)
				.build();
	}
}
