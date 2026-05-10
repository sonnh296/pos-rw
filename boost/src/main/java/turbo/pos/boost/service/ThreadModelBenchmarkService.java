package turbo.pos.boost.service;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

/**
 * Giả lập I/O-bound task bằng Thread.sleep(50ms).
 * Không có shared state, lock, hay DB call — chỉ đo pure thread scheduling overhead.
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
			// sleep(50ms) block thread hiện tại.
			// Platform: OS thread bị giữ → pool cạn kiệt khi concurrent > pool size.
			// Virtual: OS thread được unmount → scale gần như không giới hạn.
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
