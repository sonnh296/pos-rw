package turbo.pos.boost.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/outbox")
public class OutboxController {

	private static final String OUTBOX_KEY = "rewards:outbox";

	private final ObjectProvider<StringRedisTemplate> redis;
	private final JdbcClient jdbc;

	public OutboxController(ObjectProvider<StringRedisTemplate> redis, JdbcClient jdbc) {
		this.redis = redis;
		this.jdbc = jdbc;
	}

	@GetMapping("/stats")
	public Map<String, Object> stats() {
		// Redis outbox queue size
		Long queueSize = null;
		String redisStatus = "ok";
		try {
			StringRedisTemplate tpl = redis.getIfAvailable();
			if (tpl != null) {
				queueSize = tpl.opsForList().size(OUTBOX_KEY);
			} else {
				redisStatus = "disabled";
			}
		} catch (Exception e) {
			redisStatus = "error: " + e.getMessage();
		}

		// Ledger stats from MySQL
		long totalProcessed = 0;
		String lastProcessedAt = null;
		long processedLast1m = 0;
		long processedLast5m = 0;
		long processedLast1h = 0;
		List<Map<String, Object>> recentBatches = List.of();

		try {
			totalProcessed = jdbc.sql("SELECT COUNT(*) FROM reward_ledger")
					.query(Long.class)
					.single();

			lastProcessedAt = jdbc.sql("SELECT MAX(created_at) FROM reward_ledger")
					.query(String.class)
					.optional()
					.orElse(null);

			processedLast1m = jdbc.sql(
					"SELECT COUNT(*) FROM reward_ledger WHERE created_at >= NOW() - INTERVAL 1 MINUTE")
					.query(Long.class)
					.single();

			processedLast5m = jdbc.sql(
					"SELECT COUNT(*) FROM reward_ledger WHERE created_at >= NOW() - INTERVAL 5 MINUTE")
					.query(Long.class)
					.single();

			processedLast1h = jdbc.sql(
					"SELECT COUNT(*) FROM reward_ledger WHERE created_at >= NOW() - INTERVAL 1 HOUR")
					.query(Long.class)
					.single();

			recentBatches = jdbc.sql("""
					SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS minute_slot,
					       COUNT(*) AS count,
					       SUM(points_delta) AS total_points
					FROM reward_ledger
					WHERE created_at >= NOW() - INTERVAL 30 MINUTE
					GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')
					ORDER BY minute_slot DESC
					LIMIT 30
					""")
					.query(Map.class)
					.list()
					.stream()
					.map(m -> (Map<String, Object>) m)
					.toList();

		} catch (Exception e) {
			// MySQL may not be available in all setups
		}

		return Map.of(
				"redisStatus", redisStatus,
				"outboxQueueSize", queueSize == null ? -1 : queueSize,
				"totalProcessed", totalProcessed,
				"lastProcessedAt", lastProcessedAt == null ? "" : lastProcessedAt,
				"processedLast1m", processedLast1m,
				"processedLast5m", processedLast5m,
				"processedLast1h", processedLast1h,
				"recentActivity", recentBatches);
	}
}
