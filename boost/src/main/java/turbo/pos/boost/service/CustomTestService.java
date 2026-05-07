package turbo.pos.boost.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.Phase1Result;
import turbo.pos.boost.dto.Phase2Result;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.io.File;
import turbo.pos.boost.util.JtlParserUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomTestService {

    private final LockingRewardService lockingRewardService;
    private final NoLockRewardService noLockRewardService;
    private final ThreadModelBenchmarkService threadModelBenchmarkService;
    private final RewardBalanceQueryService rewardBalanceQueryService;

    private final TaskExecutor singleExecutor;
    private final TaskExecutor platformExecutor;
    private final TaskExecutor virtualExecutor;
    private final JdbcClient jdbcClient;

    private final AtomicInteger phase1Progress = new AtomicInteger(0);
    private final AtomicInteger phase2Progress = new AtomicInteger(0);

    private volatile boolean phase1Running = false;
    private volatile boolean phase2Running = false;
    private volatile boolean cancelPhase1 = false;
    private volatile boolean cancelPhase2 = false;

    public void clearResults() {
        jdbcClient.sql("TRUNCATE TABLE test_phase1_results").update();
        jdbcClient.sql("TRUNCATE TABLE test_phase2_results").update();
        log.info("Cleared custom test results tables.");
    }

    public Map<String, Object> getProgress() {
        return Map.of(
            "phase1Running", phase1Running,
            "phase1Progress", phase1Progress.get(), // max is 6 * 1000 = 6000
            "phase2Running", phase2Running,
            "phase2Progress", phase2Progress.get()  // max is 2 * 1000 = 2000
        );
    }

    public void stopTests() {
        if (phase1Running) cancelPhase1 = true;
        if (phase2Running) cancelPhase2 = true;
    }

    public synchronized void startPhase1Background(int iterations) {
        if (phase1Running) return;
        phase1Running = true;
        cancelPhase1 = false;
        phase1Progress.set(0);

        CompletableFuture.runAsync(() -> {
            try {
                jdbcClient.sql("TRUNCATE TABLE test_phase1_results").update();
                String[] executors = {"SINGLE", "PLATFORM", "VIRTUAL"};
                String[] modes = {"LOCK", "NO_LOCK"};

                outer: for (String executorName : executors) {
                    TaskExecutor executor = getExecutorByName(executorName);
                    for (String mode : modes) {
                        for (int i = 1; i <= iterations; i++) {
                            if (cancelPhase1) {
                                log.info("Phase 1 aborted by user.");
                                break outer;
                            }
                            // Chạy một vòng lặp kiểm tra Phase 1
                            runPhase1SingleIteration(executorName, executor, mode, i);
                            phase1Progress.incrementAndGet();
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Phase 1 background test failed", e);
            } finally {
                phase1Running = false;
                cancelPhase1 = false;
            }
        }, virtualExecutor); // Sử dụng Virtual Thread để điều phối (orchestration)
    }

    private void runPhase1SingleIteration(String executorName, TaskExecutor executor, String mode, int iteration) {
        String customerId = "t-p1-" + mode + "-" + UUID.randomUUID().toString().substring(0, 8);
        double amount = Math.round(ThreadLocalRandom.current().nextDouble(10.0, 100.0) * 100.0) / 100.0;
        long expectedPoints = Math.round(amount * 10) * 5; // 5 yêu cầu

        String targetPath = "/api/rewards/" + executorName.toLowerCase() + "/" + mode.toLowerCase().replace("_", "-");
        String jtlPath = "jmeter/results/custom_p1_" + executorName.toLowerCase() + "_" + mode.toLowerCase() + "_" + iteration + ".jtl";

        new File("jmeter/results").mkdirs();
        new File(jtlPath).delete();

        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "jmeter", "-n", 
                "-t", "jmeter/custom_test.jmx", 
                "-l", jtlPath,
                "-Jthreads=5",
                "-Jloops=1",
                "-JtargetPath=" + targetPath,
                "-JcustomerId=" + customerId,
                "-Jamount=" + amount,
                "-Jport=8080"
            );
            pb.start().waitFor();

            // Query actual points
            turbo.pos.boost.dto.CustomerPointsResponse pointsData = rewardBalanceQueryService.getPrimaryPoints(customerId);
            long actualPoints = pointsData != null ? pointsData.getPrimaryPoints() : 0;

            boolean isAccurate = (expectedPoints == actualPoints);
            long duration = System.currentTimeMillis() - start;

            jdbcClient.sql("""
                INSERT INTO test_phase1_results 
                (executor_type, lock_mode, iteration, amounts, expected_val, actual_val, is_accurate, duration_ms) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(executorName, mode, iteration, String.valueOf(amount) + " (x5)",
                        expectedPoints, actualPoints, isAccurate, duration)
                .update();

        } catch (Exception e) {
            log.error("Error running JMeter for Phase 1", e);
        }
    }

    public synchronized void startPhase2Background(int iterations, int totalRequestsPerIter) {
        if (phase2Running) return;
        phase2Running = true;
        cancelPhase2 = false;
        phase2Progress.set(0);

        CompletableFuture.runAsync(() -> {
            try {
                jdbcClient.sql("TRUNCATE TABLE test_phase2_results").update();
                String[] executors = {"PLATFORM", "VIRTUAL"};

                outer: for (String executorName : executors) {
                    TaskExecutor executor = getExecutorByName(executorName);
                    for (int i = 1; i <= iterations; i++) {
                        if (cancelPhase2) {
                            log.info("Phase 2 aborted by user.");
                            break outer;
                        }
                        runPhase2SingleIteration(executorName, executor, totalRequestsPerIter, i);
                        phase2Progress.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                log.error("Phase 2 test failed", e);
            } finally {
                phase2Running = false;
                cancelPhase2 = false;
            }
        }, virtualExecutor);
    }

    private void runPhase2SingleIteration(String executorName, TaskExecutor executor, int totalRequests, int iteration) {
        String targetPath = "PLATFORM".equalsIgnoreCase(executorName) 
            ? "/api/rewards/bench/platform/io" 
            : "/api/rewards/bench/virtual/io";
        
        String jmxPath = "jmeter/custom_test.jmx";
        String jtlPath = "jmeter/results/custom_p2_" + executorName.toLowerCase() + "_" + iteration + ".jtl";
        
        new File("jmeter/results").mkdirs();
        new File(jtlPath).delete();

        log.info("Running JMeter for Phase 2: {} iteration {} (requests: {})", executorName, iteration, totalRequests);
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "jmeter", "-n", 
                "-t", jmxPath, 
                "-l", jtlPath,
                "-Jthreads=" + totalRequests,
                "-Jloops=1",
                "-JtargetPath=" + targetPath,
                "-Jport=8080"
            );
            pb.start().waitFor();

            // Parse results
            JtlParserUtil.JtlMetrics metrics = JtlParserUtil.parse(jtlPath, null);
            
            jdbcClient.sql("""
                INSERT INTO test_phase2_results 
                (executor_type, iteration, duration_ms, throughput_rps, p95_ms) 
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(executorName, iteration, 0, metrics.getThroughputRps(), metrics.getP95Ms())
                .update();

        } catch (Exception e) {
            log.error("Error running JMeter for Phase 2", e);
        }
    }

    private TaskExecutor getExecutorByName(String name) {
        if ("SINGLE".equalsIgnoreCase(name)) return singleExecutor;
        if ("VIRTUAL".equalsIgnoreCase(name)) return virtualExecutor;
        return platformExecutor;
    }

    public List<Phase1Result> getPhase1Results() {
        return jdbcClient.sql("SELECT * FROM test_phase1_results ORDER BY id ASC")
                .query(Phase1Result.class)
                .list();
    }

    public List<Phase2Result> getPhase2Results() {
        return jdbcClient.sql("SELECT * FROM test_phase2_results ORDER BY id ASC")
                .query(Phase2Result.class)
                .list();
    }
}
