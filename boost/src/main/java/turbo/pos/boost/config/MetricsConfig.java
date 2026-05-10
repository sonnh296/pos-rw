package turbo.pos.boost.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Micrometer metrics.
 * - TimedAspect: kích hoạt annotation @Timed trên controller/service methods.
 * - Prometheus scrape endpoint tự động qua spring-boot-starter-actuator + micrometer-registry-prometheus.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
