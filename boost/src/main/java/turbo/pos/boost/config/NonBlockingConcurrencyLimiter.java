package turbo.pos.boost.config;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Fail-fast concurrency limiter: CAS trên AtomicInteger, không block (tránh pinning với virtual threads).
 */
@Slf4j
public class NonBlockingConcurrencyLimiter {

    private final int maxConcurrent;
    private final AtomicInteger current = new AtomicInteger(0);

    public NonBlockingConcurrencyLimiter(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
        log.info("NonBlockingConcurrencyLimiter created: max={}", maxConcurrent);
    }

    public boolean tryAcquire() {
        while (true) {
            int cur = current.get();
            if (cur >= maxConcurrent) {
                return false;
            }
            if (current.compareAndSet(cur, cur + 1)) {
                return true;
            }
        }
    }

    public void release() {
        current.decrementAndGet();
    }

    public int getAvailablePermits() {
        return Math.max(0, maxConcurrent - current.get());
    }

    public int getUsedPermits() {
        return current.get();
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public int getQueueLength() {
        return 0;
    }
}
