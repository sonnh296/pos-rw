package turbo.pos.boost.service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.repository.RewardRepository;
import turbo.pos.boost.util.RewardUtils;

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

	private final RewardRepository rewardRepository;

	@Transactional
	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		String customerId = request.getCustomerId();
		String txnId = request.getTransactionId();
		long pointsDelta = RewardUtils.calculatePoints(request.getAmount());
		BigDecimal amount = BigDecimal.valueOf(request.getAmount());

		try {
			rewardRepository.ensureCustomerBalanceRecord(customerId);
			Long balance = rewardRepository.findBalanceByCustomerIdForUpdate(customerId)
					.orElse(0L);

			try {
				rewardRepository.insertLedgerEntry(customerId, txnId, amount, pointsDelta);
			} catch (DataIntegrityViolationException e) {
				long bal = rewardRepository.findBalanceByCustomerId(customerId).orElse(0L);
				return RewardResponse.builder()
						.customerId(customerId)
						.totalPoints(bal)
						.status("DUPLICATE_TRANSACTION")
						.threadName(Thread.currentThread().toString())
						.processingTimeMs(System.currentTimeMillis() - start)
						.build();
			}

			long newBalance = balance + pointsDelta;
			rewardRepository.updateBalance(customerId, newBalance);

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
