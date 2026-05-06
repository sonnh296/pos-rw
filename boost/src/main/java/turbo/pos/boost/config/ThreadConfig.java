package turbo.pos.boost.config;

import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

/* single (serial), platform (fixed pool), virtual — so sánh mô hình thread; */
@Configuration
public class ThreadConfig {

	/** Kích thước pool platform */
	@Value("${app.executors.platform.size:50}")
	private int platformPoolSize;

	@Bean("singleExecutor")
	public TaskExecutor singleExecutor() {
		return new TaskExecutorAdapter(Executors.newSingleThreadExecutor());
	}

	@Bean("platformExecutor")
	public TaskExecutor platformExecutor() {
		int size = Math.max(1, platformPoolSize);
		return new TaskExecutorAdapter(Executors.newFixedThreadPool(size));
	}

	@Bean("virtualExecutor")
	public TaskExecutor virtualExecutor() {
		return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
	}
}
