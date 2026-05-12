package turbo.pos.boost.exception;

/**
 * Ném ra khi Redis/Redisson không truy cập được (connection/timeout),
 * để circuit breaker kích hoạt fallback sang MySQL.
 */
public class RedisUnavailableException extends RuntimeException {

	public RedisUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	public RedisUnavailableException(Throwable cause) {
		super(cause);
	}
}
