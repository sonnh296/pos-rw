package turbo.pos.boost.controller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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

@RestController
@RequestMapping("/api/rewards")
public class RewardController {

	private final NoLockRewardService noLockRewardService;
	private final LockingRewardService lockingRewardService;
	private final RewardBalanceQueryService rewardBalanceQueryService;
	
	private final TaskExecutor singleExecutor;
	private final TaskExecutor platformExecutor;
	private final TaskExecutor virtualExecutor;

	public RewardController(
			NoLockRewardService noLockRewardService,
			LockingRewardService lockingRewardService,
			RewardBalanceQueryService rewardBalanceQueryService,
			@Qualifier("singleExecutor") TaskExecutor singleExecutor,
			@Qualifier("platformExecutor") TaskExecutor platformExecutor,
			@Qualifier("virtualExecutor") TaskExecutor virtualExecutor) {
		this.noLockRewardService = noLockRewardService;
		this.lockingRewardService = lockingRewardService;
		this.rewardBalanceQueryService = rewardBalanceQueryService;
		this.singleExecutor = singleExecutor;
		this.platformExecutor = platformExecutor;
		this.virtualExecutor = virtualExecutor;
	}

	@PostMapping("/single/no-lock")
	public CompletableFuture<RewardResponse> singleNoLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.supplyAsync(() -> noLockRewardService.processReward(request), singleExecutor);
	}

	@PostMapping("/single/lock")
	public CompletableFuture<RewardResponse> singleLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.supplyAsync(() -> lockingRewardService.processReward(request), singleExecutor);
	}

	@PostMapping("/platform/no-lock")
	public CompletableFuture<RewardResponse> platformNoLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.supplyAsync(() -> noLockRewardService.processReward(request), platformExecutor);
	}

	@PostMapping("/platform/lock")
	public CompletableFuture<RewardResponse> platformLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.supplyAsync(() -> lockingRewardService.processReward(request), platformExecutor);
	}

	@PostMapping("/virtual/no-lock")
	public CompletableFuture<RewardResponse> virtualNoLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.supplyAsync(() -> noLockRewardService.processReward(request), virtualExecutor);
	}

	@PostMapping("/virtual/lock")
	public CompletableFuture<RewardResponse> virtualLock(@RequestBody TransactionRequest request) {
		return CompletableFuture.supplyAsync(() -> lockingRewardService.processReward(request), virtualExecutor);
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
