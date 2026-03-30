package turbo.pos.boost.controller;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bench")
public class BenchController {

	private final JdbcClient jdbcClient;
	private final ObjectProvider<StringRedisTemplate> redis;

	public BenchController(JdbcClient jdbcClient, ObjectProvider<StringRedisTemplate> redis) {
		this.jdbcClient = jdbcClient;
		this.redis = redis;
	}

	@GetMapping("/platform")
	@Async("platformExecutor")
	public CompletableFuture<BenchResponse> platform(
			@RequestParam(defaultValue = "50") long sleepMs,
			@RequestParam(defaultValue = "none") String work,
			@RequestParam(defaultValue = "bench") String key,
			@RequestParam(defaultValue = "true") boolean fallbackToMysqlOnRedisError) {
		return CompletableFuture.completedFuture(run("platform", sleepMs, work, key, fallbackToMysqlOnRedisError));
	}

	@GetMapping("/virtual")
	@Async("virtualExecutor")
	public CompletableFuture<BenchResponse> virtual(
			@RequestParam(defaultValue = "50") long sleepMs,
			@RequestParam(defaultValue = "none") String work,
			@RequestParam(defaultValue = "bench") String key,
			@RequestParam(defaultValue = "true") boolean fallbackToMysqlOnRedisError) {
		return CompletableFuture.completedFuture(run("virtual", sleepMs, work, key, fallbackToMysqlOnRedisError));
	}

	@GetMapping("/meta")
	public BenchMeta meta() {
		return new BenchMeta(
				OffsetDateTime.now(),
				"/api/bench/platform?sleepMs=50&work=none|sleep|mysql|redis|redis+mysql&key=bench",
				"/api/bench/virtual?sleepMs=50&work=none|sleep|mysql|redis|redis+mysql&key=bench",
				"Tip: stop Redis container to see redis error + fallback mysql latency."
		);
	}

	private BenchResponse run(String executor, long sleepMs, String work, String key, boolean fallbackToMysqlOnRedisError) {
		long start = System.currentTimeMillis();
		boolean didSleep = false;
		boolean didMysql = false;
		boolean didRedis = false;
		boolean redisError = false;
		boolean didFallbackMysql = false;
		String error = null;

		try {
			String normalized = work == null ? "none" : work.trim().toLowerCase();

			if (normalized.equals("sleep")) {
				TimeUnit.MILLISECONDS.sleep(Math.max(0, sleepMs));
				didSleep = true;
			}

			if (normalized.equals("mysql") || normalized.equals("redis+mysql") || normalized.equals("mysql+redis")) {
				mysqlPing();
				didMysql = true;
			}

			if (normalized.equals("redis") || normalized.equals("redis+mysql") || normalized.equals("mysql+redis")) {
				try {
					redisPing(key);
					didRedis = true;
				} catch (Exception e) {
					redisError = true;
					error = "REDIS_ERROR: " + safeMessage(e);
					if (fallbackToMysqlOnRedisError) {
						mysqlPing();
						didFallbackMysql = true;
					}
				}
			}

			if (normalized.equals("none")) {
				// no-op
			}

			if (!normalized.equals("sleep") && sleepMs > 0 && (normalized.equals("redis") || normalized.equals("mysql") || normalized.contains("+"))) {
				// Optional: keep the service "IO-ish" similar to rewards services.
				TimeUnit.MILLISECONDS.sleep(Math.max(0, sleepMs));
				didSleep = true;
			}

			return new BenchResponse(
					OffsetDateTime.now(),
					executor,
					Thread.currentThread().toString(),
					work,
					sleepMs,
					didSleep,
					didMysql,
					didRedis,
					redisError,
					didFallbackMysql,
					System.currentTimeMillis() - start,
					error
			);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new BenchResponse(OffsetDateTime.now(), executor, Thread.currentThread().toString(), work, sleepMs,
					didSleep, didMysql, didRedis, true, didFallbackMysql, System.currentTimeMillis() - start,
					"INTERRUPTED");
		} catch (Exception e) {
			return new BenchResponse(OffsetDateTime.now(), executor, Thread.currentThread().toString(), work, sleepMs,
					didSleep, didMysql, didRedis, redisError, didFallbackMysql, System.currentTimeMillis() - start,
					"ERROR: " + safeMessage(e));
		}
	}

	private void mysqlPing() {
		Integer one = jdbcClient.sql("SELECT 1").query(Integer.class).single();
		if (one == null || one != 1) {
			throw new IllegalStateException("MySQL ping failed");
		}
	}

	private void redisPing(String key) {
		StringRedisTemplate template = redis.getIfAvailable();
		if (template == null) {
			throw new IllegalStateException("Redis is disabled (mysql-only profile) or not configured");
		}
		String k = "bench:" + (key == null ? "bench" : key);
		template.opsForValue().increment(k);
		template.opsForValue().get(k);
	}

	private static String safeMessage(Throwable t) {
		String m = t.getMessage();
		if (m == null || m.isBlank()) return t.getClass().getSimpleName();
		return m.length() > 200 ? m.substring(0, 200) : m;
	}

	public record BenchMeta(OffsetDateTime now, String platformExample, String virtualExample, String note) {
	}

	public record BenchResponse(
			OffsetDateTime at,
			String executor,
			String thread,
			String work,
			long sleepMs,
			boolean didSleep,
			boolean didMysql,
			boolean didRedis,
			boolean redisError,
			boolean didFallbackMysql,
			long elapsedMs,
			String error
	) {
	}
}

