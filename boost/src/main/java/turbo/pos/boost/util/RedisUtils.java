package turbo.pos.boost.util;

public final class RedisUtils {

    private RedisUtils() {}

    /**
     * Kiểm tra xem exception có phải do Redis không khả dụng (connection/timeout) không.
     */
    public static boolean isRedisUnavailable(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String cn = t.getClass().getName();
            String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase();

            // Kiểm tra các lỗi kết nối hoặc timeout (Spring Redis / Redisson / Netty...)
            if (cn.contains("RedisConnection") || cn.contains("RedisTimeout") || cn.contains("Redisson")
                    || t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (msg.contains("connection") || msg.contains("refused") || msg.contains("timeout")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
