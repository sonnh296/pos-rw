package turbo.pos.boost.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RewardRepository {

    private final JdbcClient jdbc;

    public RewardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- Truy vấn Ledger (Lịch sử thưởng) ---

    public long countAllLedgerEntries() {
        return jdbc.sql("SELECT COUNT(*) FROM reward_ledger")
                .query(Long.class)
                .single();
    }

    public Optional<String> findLastProcessedAt() {
        return jdbc.sql("SELECT MAX(created_at) FROM reward_ledger")
                .query(String.class)
                .optional();
    }

    public long countLedgerEntriesSince(int intervalMinutes) {
        return jdbc.sql("SELECT COUNT(*) FROM reward_ledger WHERE created_at >= NOW() - INTERVAL ? MINUTE")
                .param(intervalMinutes)
                .query(Long.class)
                .single();
    }

    public long countLedgerEntriesSinceHours(int intervalHours) {
        return jdbc.sql("SELECT COUNT(*) FROM reward_ledger WHERE created_at >= NOW() - INTERVAL ? HOUR")
                .param(intervalHours)
                .query(Long.class)
                .single();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findRecentActivity(int limit) {
        return jdbc.sql("""
                SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS minute_slot,
                       COUNT(*) AS count,
                       SUM(points_delta) AS total_points
                FROM reward_ledger
                WHERE created_at >= NOW() - INTERVAL 30 MINUTE
                GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')
                ORDER BY minute_slot DESC
                LIMIT ?
                """)
                .param(limit)
                .query(Map.class)
                .list()
                .stream()
                .map(m -> (Map<String, Object>) m)
                .toList();
    }

    public boolean existsByTransactionId(String transactionId) {
        Long count = jdbc.sql("SELECT COUNT(*) FROM reward_ledger WHERE transaction_id = ?")
                .param(transactionId)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    public void insertLedgerEntry(String customerId, String transactionId, BigDecimal amount, long pointsDelta) {
        jdbc.sql("INSERT INTO reward_ledger (customer_id, transaction_id, amount, points_delta) VALUES (?, ?, ?, ?)")
                .params(customerId, transactionId, amount, pointsDelta)
                .update();
    }

    // --- Truy vấn Số dư (Balance) ---

    public Optional<Long> findBalanceByCustomerId(String customerId) {
        return jdbc.sql("SELECT balance FROM customer_balance WHERE customer_id = ?")
                .param(customerId)
                .query(Long.class)
                .optional();
    }

    public void ensureCustomerBalanceRecord(String customerId) {
        jdbc.sql("INSERT IGNORE INTO customer_balance (customer_id, balance) VALUES (?, 0)")
                .param(customerId)
                .update();
    }

    public Optional<Long> findBalanceByCustomerIdForUpdate(String customerId) {
        return jdbc.sql("SELECT balance FROM customer_balance WHERE customer_id = ? FOR UPDATE")
                .param(customerId)
                .query(Long.class)
                .optional();
    }

    public void updateBalance(String customerId, long newBalance) {
        jdbc.sql("UPDATE customer_balance SET balance = ? WHERE customer_id = ?")
                .params(newBalance, customerId)
                .update();
    }

    public void upsertBalance(String customerId, long balance) {
        jdbc.sql("""
                INSERT INTO customer_balance (customer_id, balance) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE balance = VALUES(balance)
                """)
                .params(customerId, balance)
                .update();
    }
}
