package turbo.pos.boost.controller;

import io.micrometer.core.annotation.Timed;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
@RequiredArgsConstructor
public class RewardController {

	private final NoLockRewardService noLockRewardService;
	private final LockingRewardService lockingRewardService;
	private final RewardBalanceQueryService rewardBalanceQueryService;
	private final ThreadModelBenchmarkService threadModelBenchmarkService;

	@PostMapping("/single/no-lock")
	@Async("singleExecutor")
	public CompletableFuture<RewardResponse> singleNoLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.completedFuture(noLockRewardService.processReward(request));
	}

	@PostMapping("/single/lock")
	@Async("singleExecutor")
	public CompletableFuture<RewardResponse> singleLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.completedFuture(lockingRewardService.processReward(request));
	}

	@PostMapping("/platform/no-lock")
	@Async("platformExecutor")
	public CompletableFuture<RewardResponse> platformNoLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.completedFuture(noLockRewardService.processReward(request));
	}

	@PostMapping("/platform/lock")
	@Async("platformExecutor")
	public CompletableFuture<RewardResponse> platformLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.completedFuture(lockingRewardService.processReward(request));
	}

	@PostMapping("/virtual/no-lock")
	@Async("virtualExecutor")
	public CompletableFuture<RewardResponse> virtualNoLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.completedFuture(noLockRewardService.processReward(request));
	}

	@PostMapping("/virtual/lock")
	@Async("virtualExecutor")
	public CompletableFuture<RewardResponse> virtualLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.completedFuture(lockingRewardService.processReward(request));
	}

	@PostMapping("/bench/single/io")
	@Async("singleExecutor")
	public CompletableFuture<RewardResponse> benchmarkSingleIo(@RequestBody(required = false) TransactionRequest request) {
		return CompletableFuture.completedFuture(threadModelBenchmarkService.processIoBoundTask(request));
	}

	@PostMapping("/bench/platform/io")
	@Async("platformExecutor")
	@Timed(value = "benchmark.io", extraTags = {"thread_model", "platform"}, percentiles = {0.5, 0.95, 0.99})
	public CompletableFuture<RewardResponse> benchmarkPlatformIo(@RequestBody(required = false) TransactionRequest request) {
		return CompletableFuture.completedFuture(threadModelBenchmarkService.processIoBoundTask(request));
	}

	@PostMapping("/bench/virtual/io")
	@Async("virtualExecutor")
	@Timed(value = "benchmark.io", extraTags = {"thread_model", "virtual"}, percentiles = {0.5, 0.95, 0.99})
	public CompletableFuture<RewardResponse> benchmarkVirtualIo(@RequestBody(required = false) TransactionRequest request) {
		return CompletableFuture.completedFuture(threadModelBenchmarkService.processIoBoundTask(request));
	}

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
}
