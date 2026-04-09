package turbo.pos.boost.config;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

/*
 * Ba mô hình threading để so sánh:
 *
 * [single] newSingleThreadExecutor — 1 OS thread duy nhất
 *   - Mọi request xếp hàng, xử lý tuần tự
 *   - Concurrency 10 cũng đã thấy latency tăng tuyến tính
 *   - Dùng làm baseline tệ nhất để thấy tại sao cần multithread
 *
 * [platform] newFixedThreadPool(200) — 200 OS threads cố định
 *   - Xử lý song song tối đa 200 request
 *   - Request thứ 201+ phải chờ thread pool → latency tăng
 *   - Mỗi thread chiếm ~1MB RAM stack, block thực sự khi chờ I/O
 *   - Lợi thế rõ khi concurrency < 200
 *
 * [virtual] newVirtualThreadPerTaskExecutor — JDK Virtual Threads (Project Loom)
 *   - 1 virtual thread per request, không giới hạn số lượng
 *   - Khi block I/O (DB, Redis, sleep) → tự nhả OS carrier thread
 *   - OS thread được tái sử dụng ngay cho virtual thread khác
 *   - Lợi thế rõ khi concurrency > 200 (vượt platform pool)
 *
 * Ngưỡng thấy rõ sự khác biệt:
 *   concurrency <  200 → single thua, platform ≈ virtual
 *   concurrency >  200 → single thua nặng, platform bắt đầu queue, virtual vẫn smooth
 *   concurrency >= 500 → virtual thắng rõ ràng so với platform
 */
@Configuration
public class ThreadConfig {

	@Bean("singleExecutor")
	public TaskExecutor singleExecutor() {
		return new TaskExecutorAdapter(Executors.newSingleThreadExecutor());
	}

	@Bean("platformExecutor")
	public TaskExecutor platformExecutor() {
		return new TaskExecutorAdapter(Executors.newFixedThreadPool(200));
	}

	@Bean("virtualExecutor")
	public TaskExecutor virtualExecutor() {
		return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
	}
}
