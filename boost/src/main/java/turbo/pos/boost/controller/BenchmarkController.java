package turbo.pos.boost.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.service.LockingRedisRewardService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final LockingRedisRewardService rewardService;

    @Qualifier("platformExecutor")
    private final TaskExecutor platformExecutor;

    @Qualifier("virtualExecutor")
    private final TaskExecutor virtualExecutor;

    @PostMapping("/internal-test")
    public Map<String, Object> runInternalBenchmark(@RequestParam(defaultValue = "5000") int count) {
        log.info("Starting internal benchmark for {} requests", count);

        // 1. Test với Platform Threads
        BenchmarkResult platformResult = runTest(platformExecutor, count, "PLATFORM");

        // 2. Test với Virtual Threads
        BenchmarkResult virtualResult = runTest(virtualExecutor, count, "VIRTUAL");

        return Map.of(
                "totalRequests", count,
                "platform", platformResult,
                "virtual", virtualResult,
                "note", "Test nội bộ để loại bỏ độ trễ mạng và overhead của JMeter/HTTP stack."
        );
    }

    private BenchmarkResult runTest(TaskExecutor executor, int count, String label) {
        long start = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String customerId = "bench-" + label + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            TransactionRequest request = new TransactionRequest(customerId, "txn-" + UUID.randomUUID(), 100.0);
            
            futures.add(CompletableFuture.runAsync(() -> {
                rewardService.processReward(request);
            }, executor));
        }

        // Đợi tất cả hoàn thành
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long durationMs = System.currentTimeMillis() - start;
        double rps = (double) count / (durationMs / 1000.0);

        return new BenchmarkResult(durationMs, Math.round(rps * 100.0) / 100.0);
    }

    public record BenchmarkResult(long durationMs, double rps) {}
}
