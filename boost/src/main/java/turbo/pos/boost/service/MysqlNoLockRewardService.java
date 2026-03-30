package turbo.pos.boost.service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

/**
 * Không FOR UPDATE (demo consistency):
 * - Khi concurrent cao, read-modify-write trên MySQL có thể bị lost update.
 * - Dùng để đối chiếu với {@link MysqlLockingRewardService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlNoLockRewardService {

	private final JdbcClient jdbcClient;

	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		String customerId = request.getCustomerId();
		String txnId = request.getTransactionId();
		long pointsDelta = Math.round(request.getAmount() * 10);
		BigDecimal amount = BigDecimal.valueOf(request.getAmount());

		try {
			if (existsTransactionId(txnId)) {
				return duplicate(customerId, start);
			}

			long balance = jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ?")
					.param(customerId)
					.query(Long.class)
					.optional()
					.orElse(0L);

			TimeUnit.MILLISECONDS.sleep(50);

			try {
				jdbcClient.sql(
						"INSERT INTO reward_ledger (customer_id, transaction_id, amount, points_delta) VALUES (?, ?, ?, ?)")
						.params(customerId, txnId, amount, pointsDelta)
						.update();
			} catch (DataIntegrityViolationException e) {
				return duplicate(customerId, start);
			}

			long newBalance = balance + pointsDelta;
			jdbcClient.sql("""
					INSERT INTO customer_balance (customer_id, balance) VALUES (?, ?)
					ON DUPLICATE KEY UPDATE balance = VALUES(balance)
					""")
					.params(customerId, newBalance)
					.update();

			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(newBalance)
					.status("SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("MysqlNoLockRewardService interrupted", e);
			return error(customerId, start);
		} catch (Exception e) {
			log.error("MysqlNoLockRewardService failed", e);
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

	private RewardResponse duplicate(String customerId, long start) {
		long bal = jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ?")
				.param(customerId)
				.query(Long.class)
				.optional()
				.orElse(0L);
		return RewardResponse.builder()
				.customerId(customerId)
				.totalPoints(bal)
				.status("DUPLICATE_TRANSACTION")
				.threadName(Thread.currentThread().toString())
				.processingTimeMs(System.currentTimeMillis() - start)
				.build();
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
