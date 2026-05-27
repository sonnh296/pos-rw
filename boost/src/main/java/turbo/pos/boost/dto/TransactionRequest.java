package turbo.pos.boost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

	@NotBlank
	private String customerId;

	@NotBlank
	private String transactionId;

	@Positive
	private double amount;
}
