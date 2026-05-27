package turbo.pos.boost.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import turbo.pos.boost.dto.RewardResponse;

public final class RewardResponseMapper {

    private RewardResponseMapper() {
    }

    public static ResponseEntity<RewardResponse> toResponse(RewardResponse body) {
        if (body == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.status(httpStatusFor(body.getStatus())).body(body);
    }

    static HttpStatus httpStatusFor(String status) {
        if (status == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status.startsWith("DUPLICATE")) {
            return HttpStatus.CONFLICT;
        }
        if ("LOCK_FAILED".equals(status)) {
            return HttpStatus.LOCKED;
        }
        if (status.startsWith("SUCCESS")) {
            return HttpStatus.OK;
        }
        if (status.startsWith("ERROR")) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
