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
public class Phase1Result {
    private Long id;
    private String executorType; // SINGLE, PLATFORM, VIRTUAL
    private String lockMode; // LOCK, NO_LOCK
    private int iteration;
    private String amounts;
    private Long expectedVal;
    private Long actualVal;
    private Boolean isAccurate;
    private Long durationMs;
    private Instant createdAt;
}
