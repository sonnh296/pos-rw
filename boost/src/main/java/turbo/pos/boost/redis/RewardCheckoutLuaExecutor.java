package turbo.pos.boost.redis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gộp idempotency + expected + points + outbox trong một round-trip Lua (lock vẫn qua Redisson).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RewardCheckoutLuaExecutor {

	public static final String HASH_KEY = "customer:points";
	public static final String OUTBOX_KEY = "rewards:outbox";
	public static final String EXPECTED_PREFIX = "expected:";
	public static final String IDEM_PREFIX = "idempotency:";

	public record LuaCheckoutResult(boolean success, boolean duplicate, long totalPoints) {
	}

	private final RedissonClient redissonClient;

	private String script;
	private String scriptSha;

	@PostConstruct
	void loadScript() throws IOException {
		try (InputStream in = new ClassPathResource("redis/reward-checkout.lua").getInputStream()) {
			script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		scriptSha = redissonClient.getScript(StringCodec.INSTANCE).scriptLoad(script);
		log.info("Loaded reward-checkout.lua (sha={})", scriptSha);
	}

	public LuaCheckoutResult execute(
			String customerId,
			String txnId,
			long pointsDelta,
			String outboxJson,
			long idempotencyTtlSeconds) {
		String idemKey = IDEM_PREFIX + txnId;
		String expectedKey = EXPECTED_PREFIX + customerId;

		@SuppressWarnings("unchecked")
		List<Object> raw = redissonClient.getScript(StringCodec.INSTANCE).evalSha(
				RScript.Mode.READ_WRITE,
				scriptSha,
				RScript.ReturnType.MULTI,
				List.of(idemKey, HASH_KEY, OUTBOX_KEY, expectedKey),
				customerId,
				Long.toString(pointsDelta),
				outboxJson,
				Long.toString(idempotencyTtlSeconds));

		int code = ((Number) raw.get(0)).intValue();
		long points = Long.parseLong(raw.get(2).toString());
		if (code == 1) {
			return new LuaCheckoutResult(true, false, points);
		}
		return new LuaCheckoutResult(false, true, points);
	}
}
