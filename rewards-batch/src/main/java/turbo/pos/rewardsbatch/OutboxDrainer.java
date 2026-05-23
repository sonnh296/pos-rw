package turbo.pos.rewardsbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Drain {@code rewards:outbox} → MySQL ledger/balance. Best-effort (log và tiếp tục). Payload JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDrainer {

    private final RedissonClient redissonClient;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.redis-list-key:rewards:outbox}")
    private String outboxKey;

    @Value("${app.outbox.batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:1000}")
    public void drainOnce() {
        long startedNs = System.nanoTime();
        List<String> items = popBatch();
        int persisted = 0;
        int duplicates = 0;
        int failed = 0;

        for (String json : items) {
            PersistResult r = persistOne(json);
            switch (r) {
                case PERSISTED -> persisted++;
                case DUPLICATE -> duplicates++;
                case FAILED -> failed++;
            }
        }
        Duration took = Duration.ofNanos(System.nanoTime() - startedNs);
        log.info(
                "Outbox sync completed (key={}, batchSize={}, fetched={}, persisted={}, duplicates={}, failed={}, tookMs={})",
                outboxKey, batchSize, items.size(), persisted, duplicates, failed, took.toMillis()
        );
    }

    private List<String> popBatch() {
        List<String> items = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            String v = (String) redissonClient.getDeque(outboxKey, StringCodec.INSTANCE).pollLast();
            if (v == null) {
                break;
            }
            items.add(v);
        }
        return items;
    }

    private PersistResult persistOne(String json) {
        try {
            RewardOutboxEvent ev = objectMapper.readValue(json, RewardOutboxEvent.class);

            jdbc.sql("INSERT IGNORE INTO customer_balance (customer_id, balance) VALUES (?, 0)")
                    .param(ev.customerId())
                    .update();

            if (!insertLedger(ev)) {
                return PersistResult.DUPLICATE;
            }

            jdbc.sql("UPDATE customer_balance SET balance = balance + ? WHERE customer_id = ?")
                    .param(ev.pointsDelta())
                    .param(ev.customerId())
                    .update();
            return PersistResult.PERSISTED;
        } catch (Exception e) {
            // Best-effort: log and continue (benchmark friendly)
            log.error("Failed to persist outbox item", e);
            return PersistResult.FAILED;
        }
    }

    private boolean insertLedger(RewardOutboxEvent ev) {
        try {
            jdbc.sql("INSERT INTO reward_ledger (customer_id, transaction_id, amount, points_delta, created_at) VALUES (?, ?, ?, ?, ?)")
                    .params(ev.customerId(), ev.transactionId(), ev.amount(), ev.pointsDelta(), ev.createdAt())
                    .update();
            return true;
        } catch (DataIntegrityViolationException dup) {
            // Idempotency at DB level: transaction_id UNIQUE.
            return false;
        }
    }

    private enum PersistResult {
        PERSISTED,
        DUPLICATE,
        FAILED
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

