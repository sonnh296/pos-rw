package turbo.pos.boost.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import turbo.pos.boost.repository.RewardRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final String OUTBOX_KEY = "rewards:outbox";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final RewardRepository rewardRepository;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Kiểm tra kích thước hàng chờ trong Redis
        Long queueSize = null;
        String redisStatus = "ok";
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis != null) {
                queueSize = redis.opsForList().size(OUTBOX_KEY);
            } else {
                redisStatus = "disabled";
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Could not connect to Redis: {}", e.getMessage());
            redisStatus = "connection_error";
        } catch (Exception e) {
            log.error("Unknown error accessing Redis: ", e);
            redisStatus = "error";
        }

        stats.put("redisStatus", redisStatus);
        stats.put("outboxQueueSize", queueSize == null ? -1 : queueSize);

        // Lấy thống kê lịch sử (Ledger) từ MySQL
        try {
            stats.put("totalProcessed", rewardRepository.countAllLedgerEntries());
            stats.put("lastProcessedAt", rewardRepository.findLastProcessedAt().orElse(""));
            stats.put("processedLast1m", rewardRepository.countLedgerEntriesSince(1));
            stats.put("processedLast5m", rewardRepository.countLedgerEntriesSince(5));
            stats.put("processedLast1h", rewardRepository.countLedgerEntriesSinceHours(1));
            stats.put("recentActivity", rewardRepository.findRecentActivity(30));
        } catch (DataAccessException e) {
            log.warn("Could not access MySQL: {}", e.getMessage());
            setDefaultDatabaseStats(stats);
        } catch (Exception e) {
            log.error("System error fetching MySQL stats: ", e);
            setDefaultDatabaseStats(stats);
        }

        return stats;
    }

    private void setDefaultDatabaseStats(Map<String, Object> stats) {
        stats.put("totalProcessed", 0L);
        stats.put("lastProcessedAt", "");
        stats.put("processedLast1m", 0L);
        stats.put("processedLast5m", 0L);
        stats.put("processedLast1h", 0L);
        stats.put("recentActivity", List.of());
    }
}
