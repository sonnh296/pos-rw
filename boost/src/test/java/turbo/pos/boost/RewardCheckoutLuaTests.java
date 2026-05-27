package turbo.pos.boost;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import turbo.pos.boost.redis.RewardCheckoutLuaExecutor;
import turbo.pos.boost.redis.RewardCheckoutLuaExecutor.LuaCheckoutResult;

@SpringBootTest(properties = {
		"app.rewards.use-lua=true",
		"app.rewards.stub-redis=false"
})
@Testcontainers
class RewardCheckoutLuaTests {

	@SuppressWarnings("resource")
	@Container
	@ServiceConnection
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
	}

	@Autowired
	private RewardCheckoutLuaExecutor luaExecutor;

	@Test
	void lua_success_then_duplicate() {
		String customerId = "lua-cust-1";
		String txnId = "lua-txn-1";
		String outbox = "{\"customerId\":\"" + customerId + "\"}";

		LuaCheckoutResult first = luaExecutor.execute(customerId, txnId, 100L, outbox, 3600L);
		assertThat(first.success()).isTrue();
		assertThat(first.duplicate()).isFalse();
		assertThat(first.totalPoints()).isEqualTo(100L);

		LuaCheckoutResult dup = luaExecutor.execute(customerId, txnId, 100L, outbox, 3600L);
		assertThat(dup.duplicate()).isTrue();
		assertThat(dup.totalPoints()).isEqualTo(100L);
	}
}
