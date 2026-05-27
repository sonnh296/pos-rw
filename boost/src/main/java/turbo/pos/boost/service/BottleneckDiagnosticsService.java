package turbo.pos.boost.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;

import turbo.pos.boost.config.ThreadConfig.ExecutorRuntimeInfo;
import turbo.pos.boost.diagnostics.RewardPhaseTiming;
import turbo.pos.boost.dto.TransactionRequest;

/**
 * Chạy mẫu request và tổng hợp thời gian từng pha + cấu hình pool để tìm nút thắt.
 */
@Service
public class BottleneckDiagnosticsService {

	private final LockingRewardService lockingRewardService;
	private final ExecutorRuntimeInfo executorRuntimeInfo;
	private final TaskExecutor platformExecutor;
	private final TaskExecutor virtualExecutor;
	private final RedisConnectionFactory redisConnectionFactory;

	@Autowired
	public BottleneckDiagnosticsService(
			LockingRewardService lockingRewardService,
			ExecutorRuntimeInfo executorRuntimeInfo,
			@Qualifier("platformExecutor") TaskExecutor platformExecutor,
			@Qualifier("virtualExecutor") TaskExecutor virtualExecutor,
			RedisConnectionFactory redisConnectionFactory) {
		this.lockingRewardService = lockingRewardService;
		this.executorRuntimeInfo = executorRuntimeInfo;
		this.platformExecutor = platformExecutor;
		this.virtualExecutor = virtualExecutor;
		this.redisConnectionFactory = redisConnectionFactory;
	}

	@Value("${spring.redis.redisson.config.singleServerConfig.connectionPoolSize:200}")
	private int redissonPoolSize;

	@Value("${app.diagnostics.samples:30}")
	private int samples;

	public Map<String, Object> analyze() {
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("configuration", configuration());
		report.put("hypotheses", hypotheses());
		report.put("phaseTimingMillis", Map.of(
				"PLATFORM", runSamples("PLATFORM", platformExecutor),
				"VIRTUAL", runSamples("VIRTUAL", virtualExecutor)));
		report.put("recommendations", recommendations());
		return report;
	}

	private Map<String, Object> configuration() {
		return Map.of(
				"platformPoolSize", executorRuntimeInfo.platformPoolSize(),
				"virtualExecutorType", executorRuntimeInfo.virtualExecutorType(),
				"redissonConnectionPoolSize", redissonPoolSize,
				"springThreadsVirtualEnabled", true,
				"redisClient", redisConnectionFactory.getClass().getSimpleName(),
				"samplesPerExecutor", samples);
	}

	private static List<Map<String, Object>> hypotheses() {
		List<Map<String, Object>> list = new ArrayList<>();
		list.add(hypothesis("H1_REDIS_POOL",
				"Redisson connection pool (~200) dùng chung",
				"Platform và Virtual đều chờ connection Redis. Khi concurrent >> pool, virtual không tạo thêm throughput.",
				"Tăng connectionPoolSize lên 512; chạy lại suite — nếu cả hai tăng gần nhau thì đúng H1."));
		list.add(hypothesis("H2_PLATFORM_POOL",
				"Platform fixed pool (200)",
				"Khi in-flight > 200, platform queue; virtual vẫn schedule task nhưng vẫn bị H1.",
				"So sánh 500 vs 2000 concurrent HTTP; chênh lệch virtual giảm khi platform pool đủ 200."));
		list.add(hypothesis("H3_REDIS_CPU",
				"Redis single-thread command processing",
				"Mỗi request ~5–6 round-trip (lock, idem, atomic, map, outbox). Redis CPU trần.",
				"redis-cli INFO stats → instantaneous_ops_per_sec khi load cao."));
		list.add(hypothesis("H4_DOUBLE_SCHEDULING",
				"HTTP Tomcat (virtual) + CompletableFuture sang executor khác",
				"Demo/checkout offload sang platform/virtual executor — thêm một lớp queue.",
				"In-process benchmark: nếu chênh lệch nhỏ hơn HTTP thì H4 có đóng góp."));
		list.add(hypothesis("H5_NO_LOCK_CONTENTION",
				"Benchmark dùng customerId/txnId unique",
				"Không tranh lock theo customer; lock vẫn tốn round-trip.",
				"Test cùng customerId nhiều thread — lock wait tăng rõ."));
		return list;
	}

	private static Map<String, Object> hypothesis(String id, String title, String detail, String howToVerify) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", id);
		m.put("title", title);
		m.put("detail", detail);
		m.put("howToVerify", howToVerify);
		return m;
	}

	private static List<String> recommendations() {
		return List.of(
				"Cân bằng redisson connectionPoolSize ≥ peak concurrent in-flight (vd. 512+).",
				"Gộp Redis ops bằng Lua script (lock + idem + update + outbox trong 1 round-trip).",
				"Production: nếu Tomcat đã virtual thread, cân nhắc gọi thẳng lockingRewardService trên request thread (bỏ supplyAsync) khi không cần isolate.",
				"Scale ngang: Redis Cluster + nhiều instance boost sau load balancer.",
				"Đo thật: redis-cli INFO, JVM thread dump, Micrometer timers trên từng pha.");
	}

	private Map<String, Object> runSamples(String label, TaskExecutor executor) {
		Map<String, long[]> sums = new LinkedHashMap<>();
		int ok = 0;
		for (int i = 0; i < samples; i++) {
			String customerId = "diag-" + label.toLowerCase() + "-" + UUID.randomUUID();
			String txnId = "txn-diag-" + UUID.randomUUID();
			try {
				Map<String, Long> phases = CompletableFuture
						.supplyAsync(() -> invokeTimed(customerId, txnId), executor)
						.get(120, TimeUnit.SECONDS);
				phases.forEach((k, v) -> sums.computeIfAbsent(k, x -> new long[1])[0] += v);
				ok++;
			} catch (Exception ignored) {
				// skip failed sample
			}
		}
		Map<String, Object> avg = new LinkedHashMap<>();
		int n = Math.max(1, ok);
		sums.forEach((k, v) -> avg.put(k, v[0] / n));
		avg.put("samplesOk", ok);
		avg.put("samplesTotal", samples);
		return avg;
	}

	private Map<String, Long> invokeTimed(String customerId, String txnId) {
		RewardPhaseTiming.begin();
		try {
			lockingRewardService.processReward(new TransactionRequest(customerId, txnId, 100.0));
			return RewardPhaseTiming.snapshotMillis();
		} finally {
			RewardPhaseTiming.clear();
		}
	}

}
