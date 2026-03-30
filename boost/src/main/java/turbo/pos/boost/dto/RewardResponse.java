package turbo.pos.boost.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardResponse {

	private String customerId;
	private long totalPoints;
	private String status;
	private String threadName;
	private long processingTimeMs;
}
