package turbo.pos.boost.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import turbo.pos.boost.service.RedisUnavailableException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@ConditionalOnProperty(name = "app.rewards.mode", havingValue = "redis", matchIfMissing = true)
public class RedissonConfig {

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redissonClient(
			@Value("${spring.data.redis.host:localhost}") String host,
			@Value("${spring.data.redis.port:6379}") int port) {
		// Lazy Redisson: không kết nối tại thời điểm khởi động; lần gọi đầu tiên sẽ tạo client (phòng trường hợp Redis chưa sẵn sàng).
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://" + host + ":" + port)
				.setConnectionPoolSize(64)
				.setConnectionMinimumIdleSize(10);

		AtomicReference<RedissonClient> delegateRef = new AtomicReference<>();
		// Proxy: mọi lời gọi RedissonClient mới thực sự gọi Redisson.create (tránh kết nối khi khởi động context).
		InvocationHandler handler = new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				String name = method.getName();

				// No-op shutdown nếu delegate chưa được tạo.
				if ("shutdown".equals(name) && (args == null || args.length == 0)) {
					RedissonClient d = delegateRef.get();
					if (d != null) d.shutdown();
					return null;
				}

				RedissonClient d = delegateRef.get();
				if (d == null) {
					synchronized (delegateRef) {
						d = delegateRef.get();
						if (d == null) {
							try {
								d = Redisson.create(config);
								delegateRef.set(d);
							} catch (Exception e) {
								throw new RedisUnavailableException("Unable to create RedissonClient (Redis unavailable)", e);
							}
						}
					}
				}
				return method.invoke(d, args);
			}
		};

		return (RedissonClient) Proxy.newProxyInstance(
				RedissonClient.class.getClassLoader(),
				new Class<?>[] { RedissonClient.class },
				handler
		);
	}
}
