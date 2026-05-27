package turbo.pos.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RewardOutboxEvent(
        String customerId,
        String transactionId,
        BigDecimal amount,
        long pointsDelta,
        OffsetDateTime createdAt
) {
    public RewardOutboxEvent(String customerId, String transactionId, double amount, long pointsDelta, OffsetDateTime createdAt) {
        this(customerId, transactionId, BigDecimal.valueOf(amount), pointsDelta, createdAt);
    }
}
