package turbo.pos.boost.config;

import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class MetricsConfig {

	@Bean
	public MeterRegistryCustomizer<MeterRegistry> concurrencyMetrics(NonBlockingConcurrencyLimiter concurrencyLimiter) {
		return registry -> {
			Gauge.builder("app.concurrency.available", concurrencyLimiter, NonBlockingConcurrencyLimiter::getAvailablePermits)
				.description("Number of available concurrency permits")
				.tag("type", "limiter")
				.register(registry);

			Gauge.builder("app.concurrency.used", concurrencyLimiter, NonBlockingConcurrencyLimiter::getUsedPermits)
				.description("Number of currently used concurrency permits")
				.tag("type", "limiter")
				.register(registry);

			Gauge.builder("app.concurrency.queued", concurrencyLimiter, NonBlockingConcurrencyLimiter::getQueueLength)
				.description("Number of threads waiting for concurrency permits")
				.tag("type", "limiter")
				.register(registry);

			log.info("Concurrency metrics registered: app.concurrency.{available,used,queued}");
		};
	}
}
