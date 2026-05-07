package turbo.pos.boost.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final int status;
    private final String errorCode;

    public AppException(String message) {
        this(message, "INTERNAL_ERROR", 500);
    }

    public AppException(String message, String errorCode, int status) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
