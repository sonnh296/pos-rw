package turbo.pos.boost.service;

import java.math.BigDecimal;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.repository.RewardRepository;
import turbo.pos.boost.util.RewardUtils;

/**
 * Không FOR UPDATE (demo consistency): read-modify-write có thể lost update khi concurrent cao.
 * Chỉ bật khi {@code app.rewards.allow-no-lock=true}; không dùng production.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rewards.allow-no-lock", havingValue = "true")
public class MysqlNoLockRewardService {

	private final RewardRepository rewardRepository;
	
	@Value("${spring.profiles.active:}")
	private String activeProfiles;
	
	@PostConstruct
	void validateEnvironment() {
		String[] profiles = activeProfiles.split(",");
		for (String profile : profiles) {
			String normalized = profile.trim().toLowerCase();
			if (normalized.contains("prod")) {
				throw new IllegalStateException(
					"MysqlNoLockRewardService is not safe for production use due to lost update risks. " +
					"This service performs read-modify-write without locking. " +
					"Use MysqlLockingRewardService instead."
				);
			}
		}
		
		log.warn("MysqlNoLockRewardService active - FOR TESTING/DEMO ONLY (no locking, lost updates possible)");
	}

	public RewardResponse processReward(TransactionRequest request) {
		long start = System.currentTimeMillis();
		String customerId = request.getCustomerId();
		String txnId = request.getTransactionId();
		long pointsDelta = RewardUtils.calculatePoints(request.getAmount());
		BigDecimal amount = BigDecimal.valueOf(request.getAmount());

		try {
			if (rewardRepository.existsByTransactionId(txnId)) {
				return duplicate(customerId, start);
			}

			long balance = rewardRepository.findBalanceByCustomerId(customerId).orElse(0L);

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
