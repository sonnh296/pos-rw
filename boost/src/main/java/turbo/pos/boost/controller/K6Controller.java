package turbo.pos.boost.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import turbo.pos.boost.service.K6Service;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k6")
@RequiredArgsConstructor
public class K6Controller {

    private final K6Service k6Service;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "running", k6Service.isRunning(),
                "logCount", k6Service.getLatestLogs().size()
        );
    }

    @PostMapping("/run")
    public Map<String, String> runTest(@RequestBody Map<String, String> params) {
        String script = params.getOrDefault("script", "phase2-throughput.js");
        String model = params.getOrDefault("model", "virtual");
        
        // Map friendly names to actual file paths in the container
        // Note: in docker compose we mount the scripts to /scripts or /app/k6/scripts
        String scriptPath = "/scripts/" + script;
        
        k6Service.runTest(scriptPath, model);
        return Map.of("message", "Test started: " + script);
    }

    @PostMapping("/stop")
    public Map<String, String> stopTest() {
        k6Service.stopTest();
        return Map.of("message", "Test stopped");
    }

    @GetMapping("/logs")
    public List<String> getLogs() {
        return k6Service.getLatestLogs();
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return k6Service.getLastSummary();
    }
}
