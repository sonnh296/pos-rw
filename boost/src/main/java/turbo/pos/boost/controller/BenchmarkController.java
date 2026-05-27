package turbo.pos.boost.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import lombok.RequiredArgsConstructor;
import turbo.pos.boost.dto.BenchmarkRampResponse;
import turbo.pos.boost.dto.BenchmarkRunResponse;
import turbo.pos.boost.service.BenchmarkService;

@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.benchmark.enabled", havingValue = "true", matchIfMissing = true)
public class BenchmarkController {

	private final BenchmarkService benchmarkService;

	@GetMapping("/meta")
	public Map<String, Object> meta() {
		return benchmarkService.meta();
	}

	/**
	 * In-process benchmark: so sánh platform vs virtual executor với tải cố định (requests/giây).
	 */
	@PostMapping("/run")
	public BenchmarkRunResponse run(
			@RequestParam(defaultValue = "false") boolean warmup,
			@RequestParam(defaultValue = "50") int requestsPerSecond,
			@RequestParam(defaultValue = "10") int durationSeconds,
			@RequestParam(defaultValue = "BOTH") String executor) {
		return benchmarkService.run(warmup, requestsPerSecond, durationSeconds, executor);
	}

	/**
	 * Ramp benchmark: tăng dần requests/giây (vd. 500 → 5000) để so sánh PLATFORM vs VIRTUAL.
	 */
	@PostMapping("/ramp")
	public BenchmarkRampResponse ramp(
			@RequestParam(required = false) String levels,
			@RequestParam(defaultValue = "false") boolean warmup,
			@RequestParam(defaultValue = "10") int durationSeconds,
			@RequestParam(defaultValue = "BOTH") String executor,
			@RequestParam(defaultValue = "0") int pauseBetweenLevelsSeconds) {
		List<Integer> parsed = benchmarkService.parseRampLevels(levels);
		return benchmarkService.runRamp(warmup, parsed, durationSeconds, executor, pauseBetweenLevelsSeconds);
	}
}
