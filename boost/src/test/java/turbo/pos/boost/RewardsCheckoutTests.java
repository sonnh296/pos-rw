package turbo.pos.boost;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RewardsCheckoutTests {

	@Container
	@ServiceConnection
	static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

	@SuppressWarnings("resource")
	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanDb() {
		jdbc.update("DELETE FROM reward_ledger");
		jdbc.update("DELETE FROM customer_balance");
	}

	@Test
	void checkout_duplicateTransaction_returns409() throws Exception {
		String customerId = "cust-dup-" + UUID.randomUUID();
		String txnId = "txn-dup-" + UUID.randomUUID();
		String body = """
				{"customerId":"%s","transactionId":"%s","amount":10.0}
				""".formatted(customerId, txnId);

		postCheckout(body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCESS"));

		postCheckout(body)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value("DUPLICATE_TRANSACTION"));
	}

	@Test
	void checkout_updatesRedisPrimaryPoints() throws Exception {
		String customerId = "cust-pts-" + UUID.randomUUID();
		String txnId = "txn-pts-" + UUID.randomUUID();
		String body = """
				{"customerId":"%s","transactionId":"%s","amount":25.0}
				""".formatted(customerId, txnId);

		postCheckout(body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPoints").value(250));

		mockMvc.perform(get("/api/rewards/points/{customerId}", customerId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primaryPoints").value(250))
				.andExpect(jsonPath("$.source").value("redis"));
	}

	private org.springframework.test.web.servlet.ResultActions postCheckout(String body) throws Exception {
		return mockMvc.perform(post("/api/rewards/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	@Test
	void checkout_invalidRequest_returns400() throws Exception {
		mockMvc.perform(post("/api/rewards/checkout")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"customerId\":\"\",\"transactionId\":\"\",\"amount\":0}"))
				.andExpect(status().isBadRequest());
	}
}
