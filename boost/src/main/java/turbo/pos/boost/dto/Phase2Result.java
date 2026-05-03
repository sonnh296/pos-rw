package turbo.pos.boost.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Phase2Result {
    private Long id;
    private String executorType; // PLATFORM, VIRTUAL
    private int iteration;
    private Long durationMs;
    private Double throughputRps;
    private Long p95Ms;
    private Instant createdAt;
}
