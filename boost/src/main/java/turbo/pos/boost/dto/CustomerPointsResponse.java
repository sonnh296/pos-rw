package turbo.pos.boost.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPointsResponse {
    private String customerId;
    private long mysqlBalance;
    private Long redisPoints;
    private long primaryPoints;
    private String source;
    private boolean inSync;
    private Object updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagedList {
        private List<CustomerPointsResponse> rows;
        private long total;
        private int limit;
        private int offset;
        private String keyword;
    }
}
