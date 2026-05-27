package turbo.pos.boost.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import turbo.pos.boost.service.BottleneckDiagnosticsService;

@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.benchmark.enabled", havingValue = "true", matchIfMissing = true)
public class BenchmarkDiagnosticsController {

	private final BottleneckDiagnosticsService bottleneckDiagnosticsService;

	/**
	 * Phân tích nút thắt: cấu hình pool, giả thuyết, thời gian từng pha Redis (mẫu 30 request).
	 */
	@GetMapping("/diagnose")
	public Map<String, Object> diagnose() {
		return bottleneckDiagnosticsService.analyze();
	}
}
