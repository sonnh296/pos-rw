package turbo.pos.boost.dto;

import java.util.List;

public record BenchmarkRampResponse(
		List<Integer> levels,
		int durationSeconds,
		boolean warmup,
		int warmupSeconds,
		int pauseBetweenLevelsSeconds,
		List<BenchmarkRampStepResult> steps,
		String note
) {
}
