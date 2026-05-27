package turbo.pos.rewardsbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import turbo.pos.common.OutboxRetryEntry;
import turbo.pos.common.RewardOutboxEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Drain {@code rewards:outbox} → MySQL; failed items → retry queue → DLQ after max attempts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDrainer {

    private final RedissonClient redissonClient;
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final BatchPopLuaExecutor batchPopLuaExecutor;

    @Value("${app.outbox.redis-list-key:rewards:outbox}")
    private String outboxKey;

    @Value("${app.outbox.retry.redis-list-key:rewards:outbox:retry}")
    private String retryKey;

    @Value("${app.outbox.dlq-key:rewards:outbox:dlq}")
    private String dlqKey;

    @Value("${app.outbox.batch-size:200}")
    private int batchSize;

    @Value("${app.outbox.retry.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.outbox.retry.base-delay-ms:1000}")
    private long retryBaseDelayMs;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:1000}")
    public void drainOnce() {
        long startedNs = System.nanoTime();
        List<String> items = popBatch(outboxKey);
        int persisted = 0;
        int duplicates = 0;
        int failed = 0;

        for (String json : items) {
            PersistResult r = persistOne(json);
            switch (r) {
                case PERSISTED -> persisted++;
                case DUPLICATE -> duplicates++;
                case FAILED -> {
                    failed++;
                    enqueueRetry(json, 0, "persist failed");
                }
            }
        }
        logDrain("primary", outboxKey, items.size(), persisted, duplicates, failed, startedNs);
    }

    @Scheduled(fixedDelayString = "${app.outbox.retry.fixed-delay-ms:2000}")
    public void drainRetryOnce() {
        long startedNs = System.nanoTime();
        long now = System.currentTimeMillis();
        List<String> rawEntries = popBatch(retryKey);
        int persisted = 0;
        int duplicates = 0;
        int failed = 0;
        int deferred = 0;

        for (String raw : rawEntries) {
            OutboxRetryEntry entry;
            try {
                entry = objectMapper.readValue(raw, OutboxRetryEntry.class);
            } catch (Exception e) {
                log.error("Invalid retry entry, moving to DLQ: {}", raw, e);
                pushDlq(raw, "invalid retry json");
                failed++;
                continue;
            }

            if (entry.nextAttemptAtEpochMs() > now) {
                pushRetryRaw(raw);
                deferred++;
                continue;
            }

            PersistResult r = persistOne(entry.payload());
            switch (r) {
                case PERSISTED -> persisted++;
                case DUPLICATE -> duplicates++;
                case FAILED -> {
                    if (entry.attempts() >= maxAttempts) {
                        pushDlq(entry.payload(), entry.lastError());
                        log.warn("Outbox item moved to DLQ after {} attempts", entry.attempts());
                        failed++;
                    } else {
                        enqueueRetry(entry.payload(), entry.attempts(), entry.lastError());
                        failed++;
                    }
                }
            }
        }
        log.info(
                "Outbox retry sync (key={}, fetched={}, persisted={}, duplicates={}, failed={}, deferred={}, tookMs={})",
                retryKey, rawEntries.size(), persisted, duplicates, failed, deferred,
                Duration.ofNanos(System.nanoTime() - startedNs).toMillis()
        );
    }

    private void logDrain(String label, String key, int fetched, int persisted, int duplicates, int failed, long startedNs) {
        Duration took = Duration.ofNanos(System.nanoTime() - startedNs);
        log.info(
                "Outbox {} sync (key={}, fetched={}, persisted={}, duplicates={}, failed={}, tookMs={})",
                label, key, fetched, persisted, duplicates, failed, took.toMillis()
        );
    }

    private List<String> popBatch(String key) {
        try {
            return batchPopLuaExecutor.popBatch(key, batchSize);
        } catch (Exception e) {
            log.warn("Lua batch pop failed, falling back to loop-based pop", e);
            List<String> items = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                String v = (String) redissonClient.getDeque(key, StringCodec.INSTANCE).pollLast();
                if (v == null) {
                    break;
                }
                items.add(v);
            }
            return items;
        }
    }

    private void enqueueRetry(String payload, int previousAttempts, String error) {
        try {
            int attempts = previousAttempts + 1;
            long delay = retryBaseDelayMs * (1L << Math.min(attempts - 1, 10));
            long nextAt = System.currentTimeMillis() + delay;
            OutboxRetryEntry entry = new OutboxRetryEntry(payload, attempts, error, nextAt);
            String json = objectMapper.writeValueAsString(entry);
            redissonClient.getDeque(retryKey, StringCodec.INSTANCE).addFirst(json);
        } catch (Exception e) {
            log.error("Failed to enqueue outbox retry, moving to DLQ", e);
            pushDlq(payload, error);
        }
    }

    private void pushRetryRaw(String raw) {
        redissonClient.getDeque(retryKey, StringCodec.INSTANCE).addFirst(raw);
    }

    /** Push to Redis DLQ; ghi file local nếu Redis lỗi. */
    private void pushDlq(String payload, String reason) {
        try {
            String dlqJson = objectMapper.writeValueAsString(
                    new DlqEntry(payload, reason, OffsetDateTime.now().toString()));
            redissonClient.getDeque(dlqKey, StringCodec.INSTANCE).addFirst(dlqJson);
            log.debug("Pushed to Redis DLQ: {}", reason);
        } catch (Exception redisError) {
            log.error("Failed to push to Redis DLQ, trying filesystem fallback", redisError);

            try {
                java.nio.file.Path dlqDir = java.nio.file.Paths.get("dlq");
                java.nio.file.Files.createDirectories(dlqDir);
                
                String timestamp = java.time.OffsetDateTime.now().toString().replace(":", "-");
                String filename = "dlq-" + timestamp + "-" + java.util.UUID.randomUUID() + ".json";
                
                String dlqJson = objectMapper.writeValueAsString(
                        new DlqEntry(payload, reason + " (Redis unavailable: " + redisError.getMessage() + ")", 
                                    java.time.OffsetDateTime.now().toString()));
                
                java.nio.file.Files.writeString(dlqDir.resolve(filename), dlqJson);
                log.info("Saved to filesystem DLQ: dlq/{}", filename);
            } catch (Exception fileError) {
                log.error("Both Redis and filesystem DLQ failed. Data may be lost: {}", payload, fileError);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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
            return false;
        }
    }

    private enum PersistResult {
        PERSISTED,
        DUPLICATE,
        FAILED
    }

    private record DlqEntry(String payload, String reason, String movedAt) {
    }
}
