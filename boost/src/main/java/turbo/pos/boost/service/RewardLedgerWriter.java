package turbo.pos.boost.service;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy writer (MySQL). Hiện tại POS flow dùng Redis + outbox (batch) là chính;
 * class này chỉ dùng cho các tình huống cần ghi DB ngay (fallback / thử nghiệm).
 */
@Service
@RequiredArgsConstructor
public class RewardLedgerWriter {

	private final JdbcClient jdbcClient;

	public boolean existsTransactionId(String transactionId) {
		Long count = jdbcClient.sql("SELECT COUNT(*) FROM reward_ledger WHERE transaction_id = ?")
				.param(transactionId)
				.query(Long.class)
				.single();
		return count != null && count > 0;
	}

	public long getBalanceForCustomer(String customerId) {
		return jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ?")
				.param(customerId)
				.query(Long.class)
				.optional()
				.orElse(0L);
	}

	/**
	 * @return số dư mới sau khi cộng điểm
	 * @throws DataIntegrityViolationException trùng {@code transaction_id}
	 */
	@Transactional
	public long appendAndIncrementBalance(String customerId, String transactionId, BigDecimal amount,
			long pointsDelta) {
		jdbcClient.sql("INSERT IGNORE INTO customer_balance (customer_id, balance) VALUES (?, 0)")
				.param(customerId)
				.update();
		Long balance = jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ? FOR UPDATE")
				.param(customerId)
				.query(Long.class)
				.single();
		jdbcClient.sql(
				"INSERT INTO reward_ledger (customer_id, transaction_id, amount, points_delta) VALUES (?, ?, ?, ?)")
				.params(customerId, transactionId, amount, pointsDelta)
				.update();
		long newBalance = balance + pointsDelta;
		jdbcClient.sql("UPDATE customer_balance SET balance = ? WHERE customer_id = ?")
				.param(newBalance)
				.param(customerId)
				.update();
		return newBalance;
	}
}
