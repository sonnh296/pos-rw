package turbo.pos.boost.dto;

import java.util.Map;

public record BenchmarkRampStepResult(
		int requestsPerSecond,
		Map<String, BenchmarkExecutorStats> results
) {
}
