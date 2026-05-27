package turbo.pos.boost.config;

import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Concurrency limiter dùng AtomicInteger (CAS) thay Semaphore để tránh carrier thread pinning.
 */
@Slf4j
@Configuration
public class ConcurrencyConfig {

    @Value("${app.concurrency.max-concurrent-requests:1500}")
    private int maxConcurrentRequests;

    @Value("${app.concurrency.enabled:true}")
    private boolean enabled;

    @Bean
    public NonBlockingConcurrencyLimiter nonBlockingConcurrencyLimiter() {
        int limit = enabled ? maxConcurrentRequests : Integer.MAX_VALUE;
        if (!enabled) {
            log.warn("ConcurrencyLimiter DISABLED - unlimited concurrency (dangerous at high load)");
        }
        return new NonBlockingConcurrencyLimiter(limit);
    }

    @Bean
    public Semaphore concurrencyLimiter() {
        return new Semaphore(Integer.MAX_VALUE);
    }
}
