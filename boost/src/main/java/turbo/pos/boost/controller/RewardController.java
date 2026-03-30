package turbo.pos.boost.controller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.service.LockingRewardService;
import turbo.pos.boost.service.NoLockRewardService;
import turbo.pos.boost.service.RewardBalanceQueryService;

@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardController {

	private final NoLockRewardService noLockRewardService;
	private final LockingRewardService lockingRewardService;
	private final RewardBalanceQueryService rewardBalanceQueryService;

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

	@GetMapping("/points/{customerId}")
	public Map<String, Object> getPoints(@PathVariable String customerId) {
		return rewardBalanceQueryService.getPrimaryPoints(customerId);
	}

	@GetMapping("/balance/compare/{customerId}")
	public Map<String, Object> compareBalances(@PathVariable String customerId) {
		return rewardBalanceQueryService.compareBalances(customerId);
	}
}
