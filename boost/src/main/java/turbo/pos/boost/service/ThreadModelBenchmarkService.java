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
			// Giả lập một tác vụ I/O-bound tốn thời gian (ví dụ: gọi API ngân hàng, query Database phức tạp)
			// Hàm sleep() này sẽ block luồng hiện tại.
			// - Đối với Platform Thread: Luồng OS vật lý bị block hoàn toàn, dẫn đến cạn kiệt Thread Pool (bottleneck).
			// - Đối với Virtual Thread: Luồng OS được giải phóng (unmount) để phục vụ request khác, 
			//   chỉ có Virtual Thread là bị block, giúp hệ thống chịu tải concurrent khổng lồ.
			TimeUnit.MILLISECONDS.sleep(50);
			return RewardResponse.builder()
					.customerId(customerId)
					.totalPoints(0L)
					.status("BENCHMARK_SUCCESS")
					.threadName(Thread.currentThread().toString())
					.processingTimeMs(System.currentTimeMillis() - start)
					.build();
		} catch (Exception e) {
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
