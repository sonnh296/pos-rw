package turbo.pos.boost.dto;

import java.util.Map;

public record BenchmarkExecutorStats(
		String executor,
		long durationMs,
		int totalRequests,
		int successCount,
		int duplicateCount,
		int lockFailedCount,
		int errorCount,
		double throughputRps,
		long latencyMinMs,
		long latencyAvgMs,
		long latencyP50Ms,
		long latencyP95Ms,
		long latencyP99Ms,
		long latencyMaxMs,
		Map<String, Integer> statusBreakdown
) {
}
