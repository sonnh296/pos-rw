package turbo.pos.boost.controller;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

	private final JdbcClient jdbc;

	public LedgerController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping
	public Map<String, Object> list(
			@RequestParam(required = false) String customerId,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset) {

		limit = Math.min(Math.max(1, limit), 200);
		offset = Math.max(0, offset);

		List<Map<String, Object>> rows;
		long total;

		if (customerId != null && !customerId.isBlank()) {
			rows = jdbc.sql("""
					SELECT id, customer_id, transaction_id, amount, points_delta, created_at
					FROM reward_ledger
					WHERE customer_id = ?
					ORDER BY created_at DESC
					LIMIT ? OFFSET ?
					""")
					.params(customerId.trim(), limit, offset)
					.query(Map.class)
					.list()
					.stream()
					.map(m -> (Map<String, Object>) m)
					.toList();

			total = jdbc.sql("SELECT COUNT(*) FROM reward_ledger WHERE customer_id = ?")
					.param(customerId.trim())
					.query(Long.class)
					.single();
		} else {
			rows = jdbc.sql("""
					SELECT id, customer_id, transaction_id, amount, points_delta, created_at
					FROM reward_ledger
					ORDER BY created_at DESC
					LIMIT ? OFFSET ?
					""")
					.params(limit, offset)
					.query(Map.class)
					.list()
					.stream()
					.map(m -> (Map<String, Object>) m)
					.toList();

			total = jdbc.sql("SELECT COUNT(*) FROM reward_ledger")
					.query(Long.class)
					.single();
		}

		return Map.of("rows", rows, "total", total, "limit", limit, "offset", offset);
	}

	@GetMapping("/stats")
	public Map<String, Object> stats() {
		long totalTxn = jdbc.sql("SELECT COUNT(*) FROM reward_ledger")
				.query(Long.class)
				.single();

		long uniqueCustomers = jdbc.sql("SELECT COUNT(DISTINCT customer_id) FROM reward_ledger")
				.query(Long.class)
				.single();

		Long totalPoints = jdbc.sql("SELECT COALESCE(SUM(points_delta), 0) FROM reward_ledger")
				.query(Long.class)
				.single();

		String lastAt = jdbc.sql("SELECT MAX(created_at) FROM reward_ledger")
				.query(String.class)
				.optional()
				.orElse(null);

		List<Map<String, Object>> topCustomers = jdbc.sql("""
				SELECT customer_id, balance
				FROM customer_balance
				ORDER BY balance DESC
				LIMIT 10
				""")
				.query(Map.class)
				.list()
				.stream()
				.map(m -> (Map<String, Object>) m)
				.toList();

		return Map.of(
				"totalTransactions", totalTxn,
				"uniqueCustomers", uniqueCustomers,
				"totalPoints", totalPoints == null ? 0 : totalPoints,
				"lastTransactionAt", lastAt == null ? "" : lastAt,
				"topCustomers", topCustomers);
	}
}
