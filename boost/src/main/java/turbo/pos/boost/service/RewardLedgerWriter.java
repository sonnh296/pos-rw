package turbo.pos.boost.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import turbo.pos.boost.repository.RewardRepository;

import java.math.BigDecimal;

/**
 * Legacy writer (MySQL). Hiện tại POS flow dùng Redis + outbox (batch) là chính;
 * class này chỉ dùng cho các tình huống cần ghi DB ngay (fallback / thử nghiệm).
 */
@Service
@RequiredArgsConstructor
public class RewardLedgerWriter {

	private final RewardRepository rewardRepository;

	public boolean existsTransactionId(String transactionId) {
		return rewardRepository.existsByTransactionId(transactionId);
	}

	public long getBalanceForCustomer(String customerId) {
		return rewardRepository.findBalanceByCustomerId(customerId).orElse(0L);
	}

	/**
	 * @return số dư mới sau khi cộng điểm
	 * @throws DataIntegrityViolationException trùng {@code transaction_id}
	 */
	@Transactional
	public long appendAndIncrementBalance(String customerId, String transactionId, BigDecimal amount,
			long pointsDelta) {
		rewardRepository.ensureCustomerBalanceRecord(customerId);
		Long balance = rewardRepository.findBalanceByCustomerIdForUpdate(customerId)
				.orElse(0L);
		
		rewardRepository.insertLedgerEntry(customerId, transactionId, amount, pointsDelta);
		
		long newBalance = balance + pointsDelta;
		rewardRepository.updateBalance(customerId, newBalance);
		
		return newBalance;
	}
}
