package turbo.pos.boost.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException e) {
        log.warn("Application business error: {} [code={}]", e.getMessage(), e.getErrorCode());
        return buildResponse(HttpStatus.valueOf(e.getStatus()), e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("Unhandled system error: ", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_ERROR", "An unexpected error occurred in the system.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("errorCode", errorCode);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
