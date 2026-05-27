package turbo.pos.boost.controller;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import turbo.pos.boost.dto.ConsistencyReportResponse;
import turbo.pos.boost.dto.CustomerPointsResponse;
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.exception.AppException;
import turbo.pos.boost.service.LockingRewardService;
import turbo.pos.boost.service.RewardBalanceQueryService;
import turbo.pos.boost.service.ShardedRewardService;
import turbo.pos.boost.service.VirtualThreadOptimizedRewardService;
import turbo.pos.boost.web.RewardResponseMapper;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/rewards")
public class RewardController {

	private final LockingRewardService lockingRewardService;
	private final ObjectProvider<ShardedRewardService> shardedRewardService;
	private final ObjectProvider<VirtualThreadOptimizedRewardService> optimizedRewardService;
	private final RewardBalanceQueryService rewardBalanceQueryService;

	@Value("${app.admin.enabled:true}")
	private boolean adminEnabled;

	@Value("${app.sharding.enabled:false}")
	private boolean shardingEnabled;

	@Value("${app.rewards.optimized:false}")
	private boolean optimizedEnabled;

	public RewardController(
			LockingRewardService lockingRewardService,
			ObjectProvider<ShardedRewardService> shardedRewardService,
			ObjectProvider<VirtualThreadOptimizedRewardService> optimizedRewardService,
			RewardBalanceQueryService rewardBalanceQueryService) {
		this.lockingRewardService = lockingRewardService;
		this.shardedRewardService = shardedRewardService;
		this.optimizedRewardService = optimizedRewardService;
		this.rewardBalanceQueryService = rewardBalanceQueryService;
	}

	@PostMapping("/checkout")
	public ResponseEntity<RewardResponse> checkout(@Valid @RequestBody TransactionRequest request) {
		// Priority: Optimized > Sharded > Default
		RewardResponse response;
		
		if (optimizedEnabled && optimizedRewardService.getIfAvailable() != null) {
			response = optimizedRewardService.getObject().processReward(request);
		} else if (shardingEnabled && shardedRewardService.getIfAvailable() != null) {
			response = shardedRewardService.getObject().processReward(request);
		} else {
			response = lockingRewardService.processReward(request);
		}
		
		return RewardResponseMapper.toResponse(response);
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
		requireAdmin();
		return rewardBalanceQueryService.clearAllPointsData();
	}

	@PostMapping("/redis/rehydrate")
	public Map<String, Object> rehydrateRedis() {
		requireAdmin();
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

	private void requireAdmin() {
		if (!adminEnabled) {
			throw new AppException("Admin operations are disabled", "ADMIN_DISABLED", 403);
		}
	}
}
