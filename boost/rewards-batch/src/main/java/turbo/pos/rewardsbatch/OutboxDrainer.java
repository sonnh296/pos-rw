package turbo.pos.rewardsbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch “cuối ngày / async”:
 * - Drain outbox từ Redis list (POS API push vào {@code rewards:outbox}).
 * - Ghi vĩnh viễn xuống MySQL (ledger + balance) để đối soát/audit.
 *
 * Thiết kế best-effort: log lỗi và tiếp tục để phục vụ benchmark/demo.
 * Payload là JSON string (transactionId, customerId, amount, pointsDelta, createdAt).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDrainer {

    private final StringRedisTemplate redis;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.redis-list-key:rewards:outbox}")
    private String outboxKey;

    @Value("${app.outbox.batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:1000}")
    public void drainOnce() {
        List<String> items = popBatch();
        if (items.isEmpty()) {
            return;
        }
        for (String json : items) {
            persistOne(json);
        }
        log.info("Drained {} outbox items", items.size());
    }

    private List<String> popBatch() {
        List<String> items = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            String v = redis.opsForList().rightPop(outboxKey);
            if (v == null) {
                break;
            }
            items.add(v);
        }
        return items;
    }

    private void persistOne(String json) {
        try {
            RewardOutboxEvent ev = objectMapper.readValue(json, RewardOutboxEvent.class);

            jdbc.sql("INSERT IGNORE INTO customer_balance (customer_id, balance) VALUES (?, 0)")
                    .param(ev.customerId())
                    .update();

            try {
                jdbc.sql("INSERT INTO reward_ledger (customer_id, transaction_id, amount, points_delta, created_at) VALUES (?, ?, ?, ?, ?)")
                        .params(ev.customerId(), ev.transactionId(), ev.amount(), ev.pointsDelta(), ev.createdAt())
                        .update();
            } catch (DataIntegrityViolationException dup) {
                // Idempotency at DB level: transaction_id UNIQUE.
                return;
            }

            jdbc.sql("UPDATE customer_balance SET balance = balance + ? WHERE customer_id = ?")
                    .param(ev.pointsDelta())
                    .param(ev.customerId())
                    .update();
        } catch (Exception e) {
            // Best-effort: log and continue (benchmark friendly)
            log.error("Failed to persist outbox item", e);
        }
    }

    public record RewardOutboxEvent(
            String customerId,
            String transactionId,
            BigDecimal amount,
            long pointsDelta,
            OffsetDateTime createdAt
    ) {
    }
}

