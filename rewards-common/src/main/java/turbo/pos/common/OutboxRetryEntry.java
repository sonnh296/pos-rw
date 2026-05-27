package turbo.pos.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OutboxRetryEntry(
        String payload,
        int attempts,
        String lastError,
        long nextAttemptAtEpochMs
) {
    public static OutboxRetryEntry first(String payload, String error, long nextAttemptAtEpochMs) {
        return new OutboxRetryEntry(payload, 1, error, nextAttemptAtEpochMs);
    }

    public OutboxRetryEntry nextAttempt(String error, long nextAttemptAtEpochMs) {
        return new OutboxRetryEntry(payload, attempts + 1, error, nextAttemptAtEpochMs);
    }
}
