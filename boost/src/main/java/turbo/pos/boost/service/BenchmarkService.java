package turbo.pos.boost.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import turbo.pos.boost.config.ThreadConfig.ExecutorRuntimeInfo;
import turbo.pos.boost.dto.BenchmarkExecutorStats;
import turbo.pos.boost.dto.BenchmarkRampResponse;
import turbo.pos.boost.dto.BenchmarkRampStepResult;
import turbo.pos.boost.dto.BenchmarkRunResponse;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.exception.AppException;

@Slf4j
@Service
public class BenchmarkService {

	private final LockingRewardService lockingRewardService;
	private final TaskExecutor platformExecutor;
	private final TaskExecutor virtualExecutor;
	private final ExecutorRuntimeInfo executorRuntimeInfo;

	@Value("${app.benchmark.max-requests-per-second:500}")
	private int maxRequestsPerSecond;

	@Value("${app.benchmark.max-duration-seconds:120}")
	private int maxDurationSeconds;

	@Value("${app.benchmark.warmup-seconds:10}")
	private int defaultWarmupSeconds;

	@Value("${app.benchmark.await-timeout-seconds:300}")
	private int awaitTimeoutSeconds;

	@Value("${app.benchmark.ramp-default-levels:500,1000,1500,2000,3000,4000,5000}")
	private String rampDefaultLevels;

	public BenchmarkService(
			LockingRewardService lockingRewardService,
			@Qualifier("platformExecutor") TaskExecutor platformExecutor,
			@Qualifier("virtualExecutor") TaskExecutor virtualExecutor,
			ExecutorRuntimeInfo executorRuntimeInfo) {
		this.lockingRewardService = lockingRewardService;
		this.platformExecutor = platformExecutor;
		this.virtualExecutor = virtualExecutor;
		this.executorRuntimeInfo = executorRuntimeInfo;
	}

	public BenchmarkRunResponse run(boolean warmup, int requestsPerSecond, int durationSeconds, String executorMode) {
		validate(requestsPerSecond, durationSeconds);
		int warmupSec = warmup ? defaultWarmupSeconds : 0;
		Map<String, BenchmarkExecutorStats> results = measureAtRps(warmupSec, requestsPerSecond, durationSeconds, executorMode);
		return new BenchmarkRunResponse(
				requestsPerSecond,
				durationSeconds,
				warmup,
				warmupSec,
				results,
				"In-process benchmark (no JMeter). Load = requestsPerSecond for durationSeconds; latencies include executor queue wait."
		);
	}

	public BenchmarkRampResponse runRamp(
			boolean warmup,
			List<Integer> levels,
			int durationSeconds,
			String executorMode,
			int pauseBetweenLevelsSeconds) {
		if (levels == null || levels.isEmpty()) {
			throw new AppException("levels must not be empty", "INVALID_BENCHMARK_PARAM", 400);
		}
		for (int rps : levels) {
			validate(rps, durationSeconds);
		}

		int warmupSec = warmup ? defaultWarmupSeconds : 0;
		List<BenchmarkRampStepResult> steps = new ArrayList<>();

		if (warmupSec > 0) {
			int warmupRps = levels.getFirst();
			log.info("Benchmark ramp warmup: rps={}, durationSec={}", warmupRps, warmupSec);
			for (String executor : resolveExecutors(executorMode)) {
				runPhase(executorFor(executor), executor, warmupRps, warmupSec, false);
			}
		}

		for (int i = 0; i < levels.size(); i++) {
			int rps = levels.get(i);
			if (i > 0 && pauseBetweenLevelsSeconds > 0) {
				pauseSeconds(pauseBetweenLevelsSeconds);
			}
			log.info("Benchmark ramp step {}/{}: rps={}", i + 1, levels.size(), rps);
			Map<String, BenchmarkExecutorStats> results = measureAtRps(0, rps, durationSeconds, executorMode);
			steps.add(new BenchmarkRampStepResult(rps, results));
		}

		return new BenchmarkRampResponse(
				List.copyOf(levels),
				durationSeconds,
				warmup,
				warmupSec,
				pauseBetweenLevelsSeconds,
				steps,
				"Ramp in-process benchmark: tăng dần requestsPerSecond; mỗi bước đo PLATFORM rồi VIRTUAL (không sleep giữa hai executor)."
		);
	}

	public List<Integer> parseRampLevels(String levelsParam) {
		String raw = (levelsParam == null || levelsParam.isBlank()) ? rampDefaultLevels : levelsParam;
		List<Integer> levels = new ArrayList<>();
		for (String part : raw.split(",")) {
			String trimmed = part.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			try {
				int value = Integer.parseInt(trimmed);
				if (value < 1) {
					throw new AppException("Each level must be >= 1", "INVALID_BENCHMARK_PARAM", 400);
				}
				levels.add(value);
			} catch (NumberFormatException e) {
				throw new AppException("Invalid level: " + trimmed, "INVALID_BENCHMARK_PARAM", 400);
			}
		}
		if (levels.isEmpty()) {
			throw new AppException("levels must not be empty", "INVALID_BENCHMARK_PARAM", 400);
		}
		return List.copyOf(levels);
	}

	private Map<String, BenchmarkExecutorStats> measureAtRps(
			int warmupSec,
			int requestsPerSecond,
			int durationSeconds,
			String executorMode) {
		Map<String, BenchmarkExecutorStats> results = new LinkedHashMap<>();
		for (String executor : resolveExecutors(executorMode)) {
			TaskExecutor taskExecutor = executorFor(executor);
			if (warmupSec > 0) {
				log.info("Benchmark warmup: executor={}, rps={}, durationSec={}", executor, requestsPerSecond, warmupSec);
				runPhase(taskExecutor, executor, requestsPerSecond, warmupSec, false);
			}
			log.info("Benchmark measured: executor={}, rps={}, durationSec={}", executor, requestsPerSecond, durationSeconds);
			results.put(executor, runPhase(taskExecutor, executor, requestsPerSecond, durationSeconds, true));
		}
		return results;
	}

	private static void pauseSeconds(int seconds) {
		try {
			Thread.sleep(seconds * 1000L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public Map<String, Object> meta() {
		return Map.of(
				"endpoints", List.of("POST /api/benchmark/run", "POST /api/benchmark/ramp"),
				"runParams", Map.of(
						"warmup", "boolean — chạy warmup trước khi đo (default false)",
						"requestsPerSecond", "int — số request/giây (default 50, max " + maxRequestsPerSecond + ")",
						"durationSeconds", "int — thời gian đo sau warmup (default 10, max " + maxDurationSeconds + ")",
						"executor", "PLATFORM | VIRTUAL | BOTH (default BOTH)"
				),
				"rampParams", Map.of(
						"levels", "comma-separated RPS, default " + rampDefaultLevels,
						"durationSeconds", "int per level (default 10)",
						"warmup", "boolean — warmup một lần ở mức RPS đầu (default false)",
						"pauseBetweenLevelsSeconds", "int — nghỉ giữa các mức RPS (default 0)",
						"executor", "PLATFORM | VIRTUAL | BOTH (default BOTH)"
				),
				"warmupSeconds", defaultWarmupSeconds,
				"limits", Map.of(
						"maxRequestsPerSecond", maxRequestsPerSecond,
						"maxDurationSeconds", maxDurationSeconds
				),
				"executors", Map.of(
						"platformPoolSize", executorRuntimeInfo.platformPoolSize(),
						"virtualExecutorType", executorRuntimeInfo.virtualExecutorType()
				),
				"rampDefaultLevels", rampDefaultLevels
		);
	}

	private BenchmarkExecutorStats runPhase(
			TaskExecutor taskExecutor,
			String executorLabel,
			int requestsPerSecond,
			int durationSeconds,
			boolean collectMetrics) {
		int totalRequests = requestsPerSecond * durationSeconds;
		CountDownLatch latch = new CountDownLatch(totalRequests);
		ConcurrentLinkedQueue<Long> latencies = collectMetrics ? new ConcurrentLinkedQueue<>() : null;
		Map<String, AtomicInteger> statusCounts = collectMetrics ? newStatusMap() : null;

		int schedulerThreads = Math.min(8, Math.max(2, requestsPerSecond / 250 + 1));
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(schedulerThreads);

		long phaseStart = System.currentTimeMillis();

		try {
			for (int sec = 0; sec < durationSeconds; sec++) {
				for (int i = 0; i < requestsPerSecond; i++) {
					long delayMs = sec * 1000L + (1000L * i / requestsPerSecond);
					String customerId = "bench-" + executorLabel.toLowerCase() + "-" + UUID.randomUUID();
					String txnId = "txn-bench-" + UUID.randomUUID();

					scheduler.schedule(() -> {
						long start = System.nanoTime();
						taskExecutor.execute(() -> {
							try {
								RewardResponse response = lockingRewardService.processReward(
										new TransactionRequest(customerId, txnId, 100.0));
								if (collectMetrics) {
									long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
									latencies.add(elapsedMs);
									incrementStatus(statusCounts, response.getStatus());
								}
							} catch (Exception e) {
								if (collectMetrics) {
									long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
									latencies.add(elapsedMs);
									incrementStatus(statusCounts, "EXCEPTION");
								}
								log.debug("Benchmark request failed: {}", e.getMessage());
							} finally {
								latch.countDown();
							}
						});
					}, delayMs, TimeUnit.MILLISECONDS);
				}
			}

			boolean finished = latch.await(awaitTimeoutSeconds, TimeUnit.SECONDS);
			if (!finished) {
				throw new AppException(
						"Benchmark timed out after " + awaitTimeoutSeconds + "s waiting for requests",
						"BENCHMARK_TIMEOUT",
						504);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AppException("Benchmark interrupted", "BENCHMARK_INTERRUPTED", 500);
		} finally {
			scheduler.shutdown();
			try {
				if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
					scheduler.shutdownNow();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				scheduler.shutdownNow();
			}
		}

		long durationMs = System.currentTimeMillis() - phaseStart;

		if (!collectMetrics) {
			return null;
		}

		return buildStats(executorLabel, durationMs, totalRequests, latencies, statusCounts);
	}

	private BenchmarkExecutorStats buildStats(
			String executor,
			long durationMs,
			int totalRequests,
			ConcurrentLinkedQueue<Long> latencies,
			Map<String, AtomicInteger> statusCounts) {
		List<Long> sorted = new ArrayList<>(latencies);
		Collections.sort(sorted);

		int success = countStatus(statusCounts, "SUCCESS");
		int duplicate = countStatusPrefix(statusCounts, "DUPLICATE");
		int lockFailed = countStatus(statusCounts, "LOCK_FAILED");
		int error = countStatusPrefix(statusCounts, "ERROR") + countStatus(statusCounts, "EXCEPTION");

		double throughput = durationMs > 0 ? totalRequests / (durationMs / 1000.0) : 0;

		Map<String, Integer> breakdown = new LinkedHashMap<>();
		statusCounts.forEach((k, v) -> breakdown.put(k, v.get()));

		return new BenchmarkExecutorStats(
				executor,
				durationMs,
				totalRequests,
				success,
				duplicate,
				lockFailed,
				error,
				Math.round(throughput * 100.0) / 100.0,
				sorted.isEmpty() ? 0 : sorted.getFirst(),
				sorted.isEmpty() ? 0 : (long) sorted.stream().mapToLong(Long::longValue).average().orElse(0),
				percentile(sorted, 0.50),
				percentile(sorted, 0.95),
				percentile(sorted, 0.99),
				sorted.isEmpty() ? 0 : sorted.getLast(),
				breakdown
		);
	}

	private static long percentile(List<Long> sorted, double p) {
		if (sorted.isEmpty()) {
			return 0;
		}
		int idx = (int) Math.ceil(p * sorted.size()) - 1;
		return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
	}

	private static Map<String, AtomicInteger> newStatusMap() {
		return new LinkedHashMap<>();
	}

	private static void incrementStatus(Map<String, AtomicInteger> counts, String status) {
		if (status == null) {
			status = "UNKNOWN";
		}
		counts.computeIfAbsent(status, k -> new AtomicInteger()).incrementAndGet();
	}

	private static int countStatus(Map<String, AtomicInteger> counts, String status) {
		AtomicInteger c = counts.get(status);
		return c == null ? 0 : c.get();
	}

	private static int countStatusPrefix(Map<String, AtomicInteger> counts, String prefix) {
		return counts.entrySet().stream()
				.filter(e -> e.getKey().startsWith(prefix))
				.mapToInt(e -> e.getValue().get())
				.sum();
	}

	private void validate(int requestsPerSecond, int durationSeconds) {
		if (requestsPerSecond < 1 || requestsPerSecond > maxRequestsPerSecond) {
			throw new AppException(
					"requestsPerSecond must be between 1 and " + maxRequestsPerSecond,
					"INVALID_BENCHMARK_PARAM",
					400);
		}
		if (durationSeconds < 1 || durationSeconds > maxDurationSeconds) {
			throw new AppException(
					"durationSeconds must be between 1 and " + maxDurationSeconds,
					"INVALID_BENCHMARK_PARAM",
					400);
		}
	}

	private static List<String> resolveExecutors(String mode) {
		if (mode == null || mode.isBlank() || "BOTH".equalsIgnoreCase(mode)) {
			return List.of("PLATFORM", "VIRTUAL");
		}
		String upper = mode.toUpperCase();
		if ("PLATFORM".equals(upper) || "VIRTUAL".equals(upper)) {
			return List.of(upper);
		}
		throw new AppException("executor must be PLATFORM, VIRTUAL, or BOTH", "INVALID_BENCHMARK_PARAM", 400);
	}

	private TaskExecutor executorFor(String executor) {
		return "PLATFORM".equals(executor) ? platformExecutor : virtualExecutor;
	}
}
