package turbo.pos.boost.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import turbo.pos.boost.service.TestRunSummaryService;

import java.util.Map;

@RestController
@RequestMapping("/api/test-runs")
@RequiredArgsConstructor
public class TestRunController {

	private final TestRunSummaryService testRunSummaryService;

	@GetMapping("/latest")
	public Map<String, Object> latest() {
		return testRunSummaryService.latestSummary();
	}

	@GetMapping("/summaries")
	public Map<String, Object> summaries(@RequestParam(defaultValue = "20") int limit) {
		return testRunSummaryService.listSummaries(limit);
	}

	@GetMapping("/phases")
	public Map<String, Object> phases() {
		return testRunSummaryService.phaseSummaries();
	}
}
