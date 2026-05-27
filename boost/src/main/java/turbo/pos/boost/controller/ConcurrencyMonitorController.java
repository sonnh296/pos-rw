package turbo.pos.boost.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import turbo.pos.boost.service.LockingRewardService;

/**
 * Concurrency Monitoring Endpoint
 * 
 * Provides real-time concurrency metrics for monitoring and alerting.
 * 
 * Endpoints:
 * - GET /api/concurrency/status - Current concurrency status
 * 
 * Example response:
 * {
 *   "enabled": true,
 *   "maxConcurrentRequests": 1500,
 *   "availablePermits": 1234,
 *   "usedPermits": 266,
 *   "queuedThreads": 0,
 *   "utilizationPercent": 17.73
 * }
 */
@RestController
@RequestMapping("/api/concurrency")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.concurrency.enabled", havingValue = "true", matchIfMissing = true)
public class ConcurrencyMonitorController {

	private final LockingRewardService lockingRewardService;

	@Value("${app.concurrency.max-concurrent-requests:1500}")
	private int maxConcurrentRequests;

	@GetMapping("/status")
	public Map<String, Object> getConcurrencyStatus() {
		int available = lockingRewardService.getAvailablePermits();
		int queued = lockingRewardService.getQueuedThreads();
		
		// Read from config (injected via @Value)
		int max = maxConcurrentRequests;
		int used = Math.max(0, max - available);
		double utilization = (double) used / max * 100;

		return Map.of(
			"enabled", true,
			"maxConcurrentRequests", max,
			"availablePermits", available,
			"usedPermits", used,
			"queuedThreads", queued,
			"utilizationPercent", Math.round(utilization * 100.0) / 100.0,
			"status", determineStatus(utilization, queued)
		);
	}

	private String determineStatus(double utilization, int queued) {
		if (queued > 10) {
			return "OVERLOADED";
		}
		if (utilization > 90) {
			return "HIGH_LOAD";
		}
		if (utilization > 70) {
			return "MODERATE_LOAD";
		}
		return "HEALTHY";
	}
}
