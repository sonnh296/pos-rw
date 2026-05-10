package turbo.pos.boost.service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.repository.RewardRepository;

/**
 * Không FOR UPDATE (demo consistency):
 * - Khi concurrent cao, read-modify-write trên MySQL có thể bị lost update.
 * - Dùng để đối chiếu với {@link MysqlLockingRewardService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlNoLockRewardService {

	private final RewardRepository rewardRepository;

	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		String customerId = request.getCustomerId();
		String txnId = request.getTransactionId();
		long pointsDelta = Math.round(request.getAmount() * 10);
		BigDecimal amount = BigDecimal.valueOf(request.getAmount());

		try {
			if (rewardRepository.existsByTransactionId(txnId)) {
				return duplicate(customerId, start);
			}

			long balance = rewardRepository.findBalanceByCustomerId(customerId).orElse(0L);

			TimeUnit.MILLISECONDS.sleep(50);

			try {
				rewardRepository.insertLedgerEntry(customerId, txnId, amount, pointsDelta);
			} catch (DataIntegrityViolationException e) {
				return duplicate(customerId, start);
			}

			long newBalance = balance + pointsDelta;
			rewardRepository.upsertBalance(customerId, newBalance);

			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(newBalance)
					.status("SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (Exception e) {
			log.error("MysqlNoLockRewardService failed", e);
			return error(customerId, start);
		}
	}

	private RewardResponse duplicate(String customerId, long start) {
		long bal = rewardRepository.findBalanceByCustomerId(customerId).orElse(0L);
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
