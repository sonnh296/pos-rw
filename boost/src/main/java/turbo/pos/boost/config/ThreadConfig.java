package turbo.pos.boost.config;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

/*
 * CHỨNG MINH: Virtual Threads vs Platform Threads
 *
 * Platform Thread Pool(200):
 *   - Tối đa 200 threads OS thực
 *   - Thread thứ 201 phải CHỜ thread trước xong mới được chạy
 *   - Mỗi thread tốn ~1MB RAM stack
 *   - Khi thread đang sleep(IO) → vẫn CHIẾM OS thread, không làm gì
 *
 * Virtual Thread (Project Loom):
 *   - Không giới hạn số lượng (hàng triệu)
 *   - Khi gặp IO (sleep, network, Redis call) → TỰ ĐỘNG nhả OS thread
 *   - OS thread được tái sử dụng cho virtual thread khác ngay lập tức
 *   - RAM: chỉ vài KB per virtual thread
 *
 * Với Thread.sleep(50ms) trong service = simulate IO:
 *   - Platform/200 + 500 users: 300 users phải chờ → latency tăng vọt
 *   - Virtual + 500 users: tất cả chạy ngay → latency thấp, throughput cao
 */
@Configuration
public class ThreadConfig {

	@Bean("platformExecutor")
	public TaskExecutor platformExecutor() {
		return new TaskExecutorAdapter(Executors.newFixedThreadPool(200));
	}

	@Bean("virtualExecutor")
	public TaskExecutor virtualExecutor() {
		return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
	}
}
