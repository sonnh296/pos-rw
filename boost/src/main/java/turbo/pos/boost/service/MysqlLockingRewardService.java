package turbo.pos.boost.service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

/**
 * Fallback path khi Redis chết (availability + consistency):
 * - Không dùng Redis/Redisson.
 * - Khóa bằng {@code SELECT ... FOR UPDATE} trên MySQL.
 * - Idempotency dựa vào UNIQUE(transaction_id) ở bảng {@code reward_ledger}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlLockingRewardService {

	private final JdbcClient jdbcClient;

	@Transactional
	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		String customerId = request.getCustomerId();
		String txnId = request.getTransactionId();
		long pointsDelta = Math.round(request.getAmount() * 10);
		BigDecimal amount = BigDecimal.valueOf(request.getAmount());

		try {
			if (existsTransactionId(txnId)) {
				long bal = getBalance(customerId);
				return RewardResponse.builder()
						.customerId(customerId)
						.totalPoints(bal)
						.status("DUPLICATE_TRANSACTION")
						.threadName(Thread.currentThread().toString())
						.processingTimeMs(System.currentTimeMillis() - start)
						.build();
			}

			TimeUnit.MILLISECONDS.sleep(50);

			jdbcClient.sql("INSERT IGNORE INTO customer_balance (customer_id, balance) VALUES (?, 0)")
					.param(customerId)
					.update();
			Long balance = jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ? FOR UPDATE")
					.param(customerId)
					.query(Long.class)
					.single();

			try {
				jdbcClient.sql(
						"INSERT INTO reward_ledger (customer_id, transaction_id, amount, points_delta) VALUES (?, ?, ?, ?)")
						.params(customerId, txnId, amount, pointsDelta)
						.update();
			} catch (DataIntegrityViolationException e) {
				long bal = getBalance(customerId);
				return RewardResponse.builder()
						.customerId(customerId)
						.totalPoints(bal)
						.status("DUPLICATE_TRANSACTION")
						.threadName(Thread.currentThread().toString())
						.processingTimeMs(System.currentTimeMillis() - start)
						.build();
			}

			long newBalance = balance + pointsDelta;
			jdbcClient.sql("UPDATE customer_balance SET balance = ? WHERE customer_id = ?")
					.param(newBalance)
					.param(customerId)
					.update();

			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(newBalance)
					.status("SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (Exception e) {
			log.error("MysqlLockingRewardService failed", e);
			return error(customerId, start);
		}
	}

	private boolean existsTransactionId(String txnId) {
		Long count = jdbcClient.sql("SELECT COUNT(*) FROM reward_ledger WHERE transaction_id = ?")
				.param(txnId)
				.query(Long.class)
				.single();
		return count != null && count > 0;
	}

	private long getBalance(String customerId) {
		return jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ?")
				.param(customerId)
				.query(Long.class)
				.optional()
				.orElse(0L);
	}

	private static RewardResponse error(String customerId, long start) {
		return RewardResponse.builder()
				.customerId(customerId)
				.totalPoints(0L)
				.status("ERROR")
				.threadName(Thread.currentThread().toString())
				.processingTimeMs(System.currentTimeMillis() - start)
				.build();
	}
}
