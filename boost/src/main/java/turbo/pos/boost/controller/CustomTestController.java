package turbo.pos.boost.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import turbo.pos.boost.dto.Phase1Result;
import turbo.pos.boost.dto.Phase2Result;
import turbo.pos.boost.service.CustomTestService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/custom-tests")
@RequiredArgsConstructor
public class CustomTestController {

    private final CustomTestService customTestService;

    @PostMapping("/clear")
    public Map<String, String> clearResults() {
        customTestService.clearResults();
        return Map.of("status", "success", "message", "Cleared custom test results.");
    }

    @PostMapping("/stop")
    public Map<String, String> stopTests() {
        customTestService.stopTests();
        return Map.of("status", "success", "message", "Stop signal sent to active tests.");
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return customTestService.getProgress();
    }

    @PostMapping("/run-phase1")
    public Map<String, Object> runPhase1(
            @RequestParam(defaultValue = "100") int iterations) {
        customTestService.startPhase1Background(iterations);
        return Map.of("status", "started", "message", "Phase 1 test started in background");
    }

    @PostMapping("/run-phase2")
    public Map<String, Object> runPhase2(
            @RequestParam(defaultValue = "100") int iterations,
            @RequestParam(defaultValue = "5000") int totalRequestsPerIter) {
        customTestService.startPhase2Background(iterations, totalRequestsPerIter);
        return Map.of("status", "started", "message", "Phase 2 test started in background");
    }

    @GetMapping("/results/phase1")
    public Map<String, Object> getPhase1Results() {
        List<Phase1Result> all = customTestService.getPhase1Results();
        
        // Group by executorType -> lockMode -> accuracy status
        Map<String, Map<String, Object>> summary = new HashMap<>();
        
        for (String exec : List.of("SINGLE", "PLATFORM", "VIRTUAL")) {
            for (String mode : List.of("LOCK", "NO_LOCK")) {
                List<Phase1Result> filtered = all.stream()
                    .filter(r -> exec.equals(r.getExecutorType()) && mode.equals(r.getLockMode()))
                    .collect(Collectors.toList());
                
                long accurate = filtered.stream().filter(Phase1Result::getIsAccurate).count();
                long total = filtered.size();
                double percent = total > 0 ? (double) accurate / total * 100 : 0;
                
                String key = exec + "_" + mode;
                summary.put(key, Map.of(
                    "accurate", accurate,
                    "total", total,
                    "percent", percent
                ));
            }
        }
        
        return Map.of(
            "summary", summary,
            "raw", all // Frontend might want to show some raw data in a table
        );
    }

    @GetMapping("/results/phase2")
    public Map<String, Object> getPhase2Results() {
        List<Phase2Result> all = customTestService.getPhase2Results();
        
        // Compute averages
        Map<String, Object> summary = new HashMap<>();
        for (String exec : List.of("PLATFORM", "VIRTUAL")) {
            List<Phase2Result> filtered = all.stream()
                .filter(r -> exec.equals(r.getExecutorType()))
                .collect(Collectors.toList());
            
            double avgThroughput = filtered.stream()
                .mapToDouble(Phase2Result::getThroughputRps)
                .average().orElse(0);
                
            double avgP95 = filtered.stream()
                .mapToLong(Phase2Result::getP95Ms)
                .average().orElse(0);
                
            summary.put(exec, Map.of(
                "avgThroughput", avgThroughput,
                "avgP95", avgP95
            ));
        }
        
        return Map.of(
            "summary", summary,
            "raw", all
        );
    }
}
