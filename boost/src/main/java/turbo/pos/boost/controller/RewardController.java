package turbo.pos.boost.controller;

import io.micrometer.core.annotation.Timed;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import turbo.pos.boost.dto.ConsistencyReportResponse;
import turbo.pos.boost.dto.CustomerPointsResponse;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.service.LockingRewardService;
import turbo.pos.boost.service.NoLockRewardService;
import turbo.pos.boost.service.RewardBalanceQueryService;
import turbo.pos.boost.service.ThreadModelBenchmarkService;

@RestController
@RequestMapping("/api/rewards")
public class RewardController {

	private final NoLockRewardService noLockRewardService;
	private final LockingRewardService lockingRewardService;
	private final RewardBalanceQueryService rewardBalanceQueryService;
	private final ThreadModelBenchmarkService threadModelBenchmarkService;

	private final ExecutorService singleExecutor;
	private final ExecutorService platformExecutor;
	private final ExecutorService virtualExecutor;

	public RewardController(
			NoLockRewardService noLockRewardService,
			LockingRewardService lockingRewardService,
			RewardBalanceQueryService rewardBalanceQueryService,
			ThreadModelBenchmarkService threadModelBenchmarkService,
			@Qualifier("singleExecutor") ExecutorService singleExecutor,
			@Qualifier("platformExecutor") ExecutorService platformExecutor,
			@Qualifier("virtualExecutor") ExecutorService virtualExecutor) {
		this.noLockRewardService = noLockRewardService;
		this.lockingRewardService = lockingRewardService;
		this.rewardBalanceQueryService = rewardBalanceQueryService;
		this.threadModelBenchmarkService = threadModelBenchmarkService;
		this.singleExecutor = singleExecutor;
		this.platformExecutor = platformExecutor;
		this.virtualExecutor = virtualExecutor;
	}

	// ── Single thread ──

	@PostMapping("/single/no-lock")
	public DeferredResult<RewardResponse> singleNoLock(@RequestBody TransactionRequest request) {
		return submit(singleExecutor, () -> noLockRewardService.processReward(request));
	}

	@PostMapping("/single/lock")
	public DeferredResult<RewardResponse> singleLock(@RequestBody TransactionRequest request) {
		return submit(singleExecutor, () -> lockingRewardService.processReward(request));
	}

	// ── Platform thread pool (fixed, default 200) ──

	@PostMapping("/platform/no-lock")
	public DeferredResult<RewardResponse> platformNoLock(@RequestBody TransactionRequest request) {
		return submit(platformExecutor, () -> noLockRewardService.processReward(request));
	}

	@PostMapping("/platform/lock")
	public DeferredResult<RewardResponse> platformLock(@RequestBody TransactionRequest request) {
		return submit(platformExecutor, () -> lockingRewardService.processReward(request));
	}

	// ── Virtual thread (per-task, unbounded) ──

	@PostMapping("/virtual/no-lock")
	public DeferredResult<RewardResponse> virtualNoLock(@RequestBody TransactionRequest request) {
		return submit(virtualExecutor, () -> noLockRewardService.processReward(request));
	}

	@PostMapping("/virtual/lock")
	public DeferredResult<RewardResponse> virtualLock(@RequestBody TransactionRequest request) {
		return submit(virtualExecutor, () -> lockingRewardService.processReward(request));
	}

	// ── Benchmark (I/O-bound, sleep 50ms) ──

	@PostMapping("/bench/single/io")
	public DeferredResult<RewardResponse> benchmarkSingleIo(@RequestBody(required = false) TransactionRequest request) {
		return submit(singleExecutor, () -> threadModelBenchmarkService.processIoBoundTask(request));
	}

	@PostMapping("/bench/platform/io")
	@Timed(value = "benchmark.io", extraTags = {"thread_model", "platform"}, percentiles = {0.5, 0.95, 0.99})
	public DeferredResult<RewardResponse> benchmarkPlatformIo(@RequestBody(required = false) TransactionRequest request) {
		return submit(platformExecutor, () -> threadModelBenchmarkService.processIoBoundTask(request));
	}

	@PostMapping("/bench/virtual/io")
	@Timed(value = "benchmark.io", extraTags = {"thread_model", "virtual"}, percentiles = {0.5, 0.95, 0.99})
	public DeferredResult<RewardResponse> benchmarkVirtualIo(@RequestBody(required = false) TransactionRequest request) {
		return submit(virtualExecutor, () -> threadModelBenchmarkService.processIoBoundTask(request));
	}

	// ── Query / Admin ──

	@GetMapping("/points/{customerId}")
	public CustomerPointsResponse getPoints(@PathVariable String customerId) {
		return rewardBalanceQueryService.getPrimaryPoints(customerId);
	}

	@GetMapping("/points")
	public CustomerPointsResponse.PagedList listPoints(
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(required = false) String keyword) {
		return rewardBalanceQueryService.listCustomerPoints(limit, offset, keyword);
	}

	@PostMapping("/points/clear")
	public Map<String, Object> clearAllPoints() {
		return rewardBalanceQueryService.clearAllPointsData();
	}

	@PostMapping("/redis/rehydrate")
	public Map<String, Object> rehydrateRedis() {
		return rewardBalanceQueryService.rehydrateRedisFromMysql();
	}

	@GetMapping("/balance/compare/{customerId}")
	public CustomerPointsResponse compareBalances(@PathVariable String customerId) {
		return rewardBalanceQueryService.compareBalances(customerId);
	}

	@GetMapping("/consistency/global")
	public ConsistencyReportResponse globalConsistency() {
		return rewardBalanceQueryService.globalConsistencyReport();
	}

	// ── Helper ──

	/**
	 * Submit task vào executor cụ thể, trả DeferredResult cho Tomcat.
	 * Thread nào chạy task phụ thuộc hoàn toàn vào executor được truyền vào.
	 */
	private DeferredResult<RewardResponse> submit(ExecutorService executor, java.util.concurrent.Callable<RewardResponse> task) {
		DeferredResult<RewardResponse> result = new DeferredResult<>(30_000L);
		executor.submit(() -> {
			try {
				result.setResult(task.call());
			} catch (Exception e) {
				result.setErrorResult(e);
			}
		});
		return result;
	}
}
