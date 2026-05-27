package turbo.pos.rewardsbatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import turbo.pos.common.RewardOutboxEvent;

@SpringBootTest
@Testcontainers
class OutboxDrainerRetryTests {

	@Container
	static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
			.withDatabaseName("rewards")
			.withUsername("rewards")
			.withPassword("rewards");

	@SuppressWarnings("resource")
	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
		registry.add("app.outbox.fixed-delay-ms", () -> "500");
		registry.add("app.outbox.retry.fixed-delay-ms", () -> "500");
		registry.add("app.outbox.retry.max-attempts", () -> "1");
	}

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM reward_ledger");
		jdbc.update("DELETE FROM customer_balance");
		redissonClient.getDeque("rewards:outbox", StringCodec.INSTANCE).delete();
		redissonClient.getDeque("rewards:outbox:retry", StringCodec.INSTANCE).delete();
		redissonClient.getDeque("rewards:outbox:dlq", StringCodec.INSTANCE).delete();
	}

	@Test
	void invalidPayload_goesToRetryThenDlq() throws Exception {
		redissonClient.getDeque("rewards:outbox", StringCodec.INSTANCE).addFirst("{not-json}");

		await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).until(() -> {
			int dlq = redissonClient.getDeque("rewards:outbox:dlq", StringCodec.INSTANCE).size();
			int primary = redissonClient.getDeque("rewards:outbox", StringCodec.INSTANCE).size();
			return dlq >= 1 && primary == 0;
		});
	}

	@Test
	void validPayload_persistedToMysql() throws Exception {
		String txnId = "txn-batch-" + System.nanoTime();
		RewardOutboxEvent ev = new RewardOutboxEvent(
				"cust-1", txnId, 10.0, 100L, OffsetDateTime.now());
		String json = objectMapper.writeValueAsString(ev);
		redissonClient.getDeque("rewards:outbox", StringCodec.INSTANCE).addFirst(json);

		await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).until(() -> {
			Integer count = jdbc.queryForObject(
					"SELECT COUNT(*) FROM reward_ledger WHERE transaction_id = ?",
					Integer.class,
					txnId);
			return count != null && count == 1;
		});

		Long balance = jdbc.queryForObject(
				"SELECT balance FROM customer_balance WHERE customer_id = ?",
				Long.class,
				"cust-1");
		assertThat(balance).isEqualTo(100L);
	}
}
