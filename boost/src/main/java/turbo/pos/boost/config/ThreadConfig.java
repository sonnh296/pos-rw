package turbo.pos.boost.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThreadConfig {

	@Value("${app.executors.platform.size:200}")
	private int platformPoolSize;

	@Bean("singleExecutor")
	public ExecutorService singleExecutor() {
		return Executors.newSingleThreadExecutor();
	}

	@Bean("platformExecutor")
	public ExecutorService platformExecutor() {
		return Executors.newFixedThreadPool(Math.max(1, platformPoolSize));
	}

	@Bean("virtualExecutor")
	public ExecutorService virtualExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}
}
