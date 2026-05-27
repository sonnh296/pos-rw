package turbo.pos.boost.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.service.LockingRewardService;
import turbo.pos.boost.service.NoLockRewardService;
import turbo.pos.boost.web.RewardResponseMapper;

@RestController
@RequestMapping("/api/rewards/demo")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class RewardDemoController {

	private final NoLockRewardService noLockRewardService;
	private final LockingRewardService lockingRewardService;
	private final TaskExecutor singleExecutor;
	private final TaskExecutor platformExecutor;
	private final TaskExecutor virtualExecutor;

	public RewardDemoController(
			NoLockRewardService noLockRewardService,
			LockingRewardService lockingRewardService,
			@Qualifier("singleExecutor") TaskExecutor singleExecutor,
			@Qualifier("platformExecutor") TaskExecutor platformExecutor,
			@Qualifier("virtualExecutor") TaskExecutor virtualExecutor) {
		this.noLockRewardService = noLockRewardService;
		this.lockingRewardService = lockingRewardService;
		this.singleExecutor = singleExecutor;
		this.platformExecutor = platformExecutor;
		this.virtualExecutor = virtualExecutor;
	}

	@PostMapping("/single/no-lock")
	public CompletableFuture<ResponseEntity<RewardResponse>> singleNoLock(@Valid @RequestBody TransactionRequest request) {
		return asyncOn(singleExecutor, () -> noLockRewardService.processReward(request));
	}

	@PostMapping("/single/lock")
	public CompletableFuture<ResponseEntity<RewardResponse>> singleLock(@Valid @RequestBody TransactionRequest request) {
		return asyncOn(singleExecutor, () -> lockingRewardService.processReward(request));
	}

	@PostMapping("/platform/no-lock")
	public CompletableFuture<ResponseEntity<RewardResponse>> platformNoLock(@Valid @RequestBody TransactionRequest request) {
		return asyncOn(platformExecutor, () -> noLockRewardService.processReward(request));
	}

	@PostMapping("/platform/lock")
	public CompletableFuture<ResponseEntity<RewardResponse>> platformLock(@Valid @RequestBody TransactionRequest request) {
		return asyncOn(platformExecutor, () -> lockingRewardService.processReward(request));
	}

	@PostMapping("/virtual/no-lock")
	public CompletableFuture<ResponseEntity<RewardResponse>> virtualNoLock(@Valid @RequestBody TransactionRequest request) {
		return asyncOn(virtualExecutor, () -> noLockRewardService.processReward(request));
	}

	@PostMapping("/virtual/lock")
	public CompletableFuture<ResponseEntity<RewardResponse>> virtualLock(@Valid @RequestBody TransactionRequest request) {
		return asyncOn(virtualExecutor, () -> lockingRewardService.processReward(request));
	}

	private CompletableFuture<ResponseEntity<RewardResponse>> asyncOn(
			TaskExecutor executor,
			java.util.function.Supplier<RewardResponse> supplier) {
		return CompletableFuture
				.supplyAsync(supplier, executor)
				.thenApply(RewardResponseMapper::toResponse);
	}
}
