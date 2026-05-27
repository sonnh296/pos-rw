package turbo.pos.boost.dto;

import java.util.Map;

public record BenchmarkRunResponse(
		int requestsPerSecond,
		int durationSeconds,
		boolean warmup,
		int warmupSeconds,
		Map<String, BenchmarkExecutorStats> results,
		String note
) {
}
