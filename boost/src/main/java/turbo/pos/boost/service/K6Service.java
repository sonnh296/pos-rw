package turbo.pos.boost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import turbo.pos.boost.entity.BenchmarkResult;
import turbo.pos.boost.repository.BenchmarkResultRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class K6Service {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    private final ObjectMapper objectMapper;
    private final BenchmarkResultRepository repository;
    private Process currentProcess;
    private Map<String, Object> lastSummary;

    public boolean isRunning() {
        return isRunning.get();
    }

    public List<String> getLatestLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public Map<String, Object> getLastSummary() {
        return lastSummary;
    }

    public void stopTest() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            isRunning.set(false);
            addLog("--- TEST STOPPED BY USER ---");
        }
    }

    public void runTest(String scriptPath, String model) {
        if (isRunning.get()) {
            throw new IllegalStateException("A test is already running");
        }

        logs.clear();
        isRunning.set(true);
        lastSummary = null;
        addLog("Starting K6 test: " + scriptPath);

        CompletableFuture.runAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "k6", "run",
                        "-o", "experimental-prometheus-rw",
                        "--env", "K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write",
                        "--env", "BASE_URL=http://localhost:8080",
                        scriptPath
                );
                
                pb.redirectErrorStream(true);
                currentProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        addLog(line);
                    }
                }

                int exitCode = currentProcess.waitFor();
                addLog("K6 finished with exit code: " + exitCode);
                
                // Read summary JSON
                File summaryFile = new File("/scripts/last_summary.json");
                if (summaryFile.exists()) {
                    lastSummary = objectMapper.readValue(summaryFile, Map.class);
                    saveToDatabase(scriptPath, lastSummary);
                    addLog("Summary data saved to database.");
                }

            } catch (Exception e) {
                addLog("Error running K6: " + e.getMessage());
                log.error("K6 execution failed", e);
            } finally {
                isRunning.set(false);
            }
        });
    }

    private void saveToDatabase(String scriptPath, Map<String, Object> summary) {
        try {
            Long totalRequests = Long.valueOf(summary.get("total_requests").toString());
            
            // Save Platform Result
            Map<String, Object> pMap = (Map<String, Object>) summary.get("platform");
            repository.save(BenchmarkResult.builder()
                    .testName(scriptPath)
                    .threadModel("platform")
                    .rps(Double.valueOf(pMap.get("rps").toString()))
                    .p95(Double.valueOf(pMap.get("p95").toString()))
                    .totalRequests(totalRequests)
                    .createdAt(LocalDateTime.now())
                    .build());

            // Save Virtual Result
            Map<String, Object> vMap = (Map<String, Object>) summary.get("virtual");
            repository.save(BenchmarkResult.builder()
                    .testName(scriptPath)
                    .threadModel("virtual")
                    .rps(Double.valueOf(vMap.get("rps").toString()))
                    .p95(Double.valueOf(vMap.get("p95").toString()))
                    .totalRequests(totalRequests)
                    .createdAt(LocalDateTime.now())
                    .build());
            
            log.info("Successfully saved k6 results to DB");
        } catch (Exception e) {
            log.error("Failed to save benchmark results to DB", e);
        }
    }

    private void addLog(String line) {
        synchronized (logs) {
            logs.add(line);
            if (logs.size() > 500) logs.remove(0);
        }
    }
}
