package turbo.pos.boost.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsistencyReportResponse {
    private long mysqlTotalPoints;
    private long mysqlCustomerCount;
    private Long redisTotalPoints;
    private Long redisCustomerCount;
    private Long outboxPending;
    private Long expectedTotalPoints;
    private String verdict;
    private Long diff;
    private String promiseVerdict;
    private Long promiseDiff;
    private String note;
}
