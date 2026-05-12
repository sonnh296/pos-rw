package turbo.pos.boost.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Repository
public class RewardBalanceQueryRepository {

    private final JdbcClient jdbcClient;

    public RewardBalanceQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long getMysqlBalance(String customerId) {
        return jdbcClient.sql("SELECT balance FROM customer_balance WHERE customer_id = ?")
                .param(customerId)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    public long getExpectedPointsFromMysqlLedger(String customerId) {
        return jdbcClient.sql("SELECT COALESCE(SUM(points_delta), 0) FROM reward_ledger WHERE customer_id = ?")
                .param(customerId)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    public long getTotalMysqlBalance() {
        return jdbcClient.sql("SELECT COALESCE(SUM(balance), 0) FROM customer_balance")
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    public long getTotalMysqlCustomers() {
        return jdbcClient.sql("SELECT COUNT(*) FROM customer_balance")
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    public List<Map<String, Object>> searchCustomerBalances(String likeKeyword, int limit, int offset) {
        return jdbcClient.sql("""
                SELECT customer_id, balance, updated_at
                FROM customer_balance
                WHERE customer_id LIKE ?
                ORDER BY updated_at DESC
                LIMIT ? OFFSET ?
                """)
                .params(likeKeyword, limit, offset)
                .query((rs, rowNum) -> toBalanceRow(rs))
                .list();
    }

    public long countCustomerBalancesByKeyword(String likeKeyword) {
        return jdbcClient.sql("SELECT COUNT(*) FROM customer_balance WHERE customer_id LIKE ?")
                .param(likeKeyword)
                .query(Long.class)
                .single();
    }

    public List<Map<String, Object>> getCustomerBalances(int limit, int offset) {
        return jdbcClient.sql("""
                SELECT customer_id, balance, updated_at
                FROM customer_balance
                ORDER BY updated_at DESC
                LIMIT ? OFFSET ?
                """)
                .params(limit, offset)
                .query((rs, rowNum) -> toBalanceRow(rs))
                .list();
    }

    public long countAllCustomerBalances() {
        return jdbcClient.sql("SELECT COUNT(*) FROM customer_balance")
                .query(Long.class)
                .single();
    }

    public long deleteAllRewardLedger() {
        return jdbcClient.sql("DELETE FROM reward_ledger").update();
    }

    public long deleteAllCustomerBalance() {
        return jdbcClient.sql("DELETE FROM customer_balance").update();
    }

    public List<Map<String, Object>> getAllCustomerBalances() {
        return jdbcClient.sql("""
                SELECT customer_id, balance
                FROM customer_balance
                """)
                .query((rs, rowNum) -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("customerId", rs.getString("customer_id"));
                    r.put("balance", rs.getLong("balance"));
                    return r;
                })
                .list();
    }

    private static Map<String, Object> toBalanceRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customer_id", rs.getString("customer_id"));
        row.put("balance", rs.getLong("balance"));
        row.put("updated_at", rs.getObject("updated_at"));
        return row;
    }
}
