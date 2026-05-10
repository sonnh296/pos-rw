package turbo.pos.boost.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@Table("benchmark_results")
public class BenchmarkResult {
    @Id
    private Long id;
    private String testName;
    private String threadModel;
    private Double rps;
    private Double p95;
    private Long totalRequests;
    private LocalDateTime createdAt;
}
