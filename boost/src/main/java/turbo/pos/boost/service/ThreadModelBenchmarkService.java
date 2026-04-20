package turbo.pos.boost.service;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

/**
 * Benchmark-only service for comparing thread models with minimal business noise.
 * This simulates blocking I/O and avoids shared state, lock contention, and DB/Redis calls.
 */
@Service
public class ThreadModelBenchmarkService {

	public RewardResponse processIoBoundTask(TransactionRequest request) {
		long start = System.currentTimeMillis();
		String customerId = request == null ? "benchmark-user" : request.getCustomerId();
		if (customerId == null || customerId.isBlank()) {
			customerId = "benchmark-user";
		}

		try {
			TimeUnit.MILLISECONDS.sleep(50);
			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(0L)
					.status("BENCHMARK_SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(0L)
					.status("BENCHMARK_INTERRUPTED")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		}
	}
}
