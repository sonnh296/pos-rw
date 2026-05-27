package turbo.pos.boost;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class BenchmarkControllerTests {

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

	@Test
	void run_benchmark_returnsStats() throws Exception {
		mockMvc.perform(post("/api/benchmark/run")
						.param("warmup", "false")
						.param("requestsPerSecond", "5")
						.param("durationSeconds", "2")
						.param("executor", "VIRTUAL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requestsPerSecond").value(5))
				.andExpect(jsonPath("$.results.VIRTUAL.totalRequests").value(10))
				.andExpect(jsonPath("$.results.VIRTUAL.throughputRps").isNumber());
	}

	@Test
	void ramp_benchmark_returnsSteps() throws Exception {
		mockMvc.perform(post("/api/benchmark/ramp")
						.param("levels", "5,10")
						.param("durationSeconds", "1")
						.param("warmup", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.steps.length()").value(2))
				.andExpect(jsonPath("$.steps[0].requestsPerSecond").value(5))
				.andExpect(jsonPath("$.steps[0].results.VIRTUAL.throughputRps").isNumber());
	}

	@Test
	void run_benchmark_rejectsInvalidRps() throws Exception {
		mockMvc.perform(post("/api/benchmark/run")
						.param("requestsPerSecond", "99999")
						.param("durationSeconds", "1"))
				.andExpect(status().isBadRequest());
	}
}
