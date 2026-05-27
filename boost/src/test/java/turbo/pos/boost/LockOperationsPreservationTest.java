package turbo.pos.boost;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Lock acquire/release cơ bản vẫn hoạt động sau khi chuyển sang Lua release. */
@SpringBootTest
@Testcontainers
public class LockOperationsPreservationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.redis.redisson.config.singleServerConfig.address",
                () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
    }

    @Autowired
    private RedissonClient redissonClient;

    /**
     * Preservation Property 1: Lock Acquisition Works
     * 
     * Verifies that a thread can successfully acquire a lock when no other thread holds it.
     * This is the fundamental lock operation that must continue working.
     */
    @Test
    void testLockAcquisitionWorks() {
        String lockKey = "test:lock:acquisition-" + UUID.randomUUID();
        String lockValue = "owner-" + UUID.randomUUID();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Acquire lock
        boolean acquired = bucket.setIfAbsent(lockValue, Duration.ofSeconds(10));
        
        assertThat(acquired)
                .as("Lock should be acquired when no other thread holds it")
                .isTrue();
        
        // Verify lock value
        String storedValue = bucket.get();
        assertThat(storedValue)
                .as("Stored lock value should match the value we set")
                .isEqualTo(lockValue);
        
        // Cleanup
        bucket.delete();
    }

    /**
     * Preservation Property 2: Lock Prevents Concurrent Acquisition
     * 
     * Verifies that when one thread holds a lock, another thread cannot acquire it.
     * This is the core mutual exclusion property.
     */
    @Test
    void testLockPreventsConcurrentAcquisition() {
        String lockKey = "test:lock:mutual-exclusion-" + UUID.randomUUID();
        String lockValueA = "owner-A-" + UUID.randomUUID();
        String lockValueB = "owner-B-" + UUID.randomUUID();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires lock
        boolean acquiredA = bucket.setIfAbsent(lockValueA, Duration.ofSeconds(10));
        assertThat(acquiredA).isTrue();
        
        // Thread B tries to acquire same lock
        boolean acquiredB = bucket.setIfAbsent(lockValueB, Duration.ofSeconds(10));
        
        assertThat(acquiredB)
                .as("Second thread should NOT acquire lock while first thread holds it")
                .isFalse();
        
        // Verify lock still belongs to Thread A
        String currentValue = bucket.get();
        assertThat(currentValue)
                .as("Lock should still belong to Thread A")
                .isEqualTo(lockValueA);
        
        // Cleanup
        bucket.delete();
    }

    /**
     * Preservation Property 3: Lock Owner Can Release Their Own Lock
     * 
     * Verifies that the lock owner can successfully release their lock.
     * This is the happy path that must continue working after the fix.
     */
    @Test
    void testLockOwnerCanReleaseOwnLock() {
        String lockKey = "test:lock:release-" + UUID.randomUUID();
        String lockValue = "owner-" + UUID.randomUUID();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Acquire lock
        bucket.setIfAbsent(lockValue, Duration.ofSeconds(10));
        
        // Simulate current release logic (GET then DELETE)
        String currentValue = bucket.get();
        boolean released = false;
        if (lockValue.equals(currentValue)) {
            released = bucket.delete();
        }
        
        assertThat(released)
                .as("Lock owner should successfully release their own lock")
                .isTrue();
        
        // Verify lock is gone
        String afterRelease = bucket.get();
        assertThat(afterRelease)
                .as("Lock should be removed after release")
                .isNull();
    }

    /**
     * Preservation Property 4: Non-Owner Cannot Release Lock
     * 
     * Verifies that a thread that doesn't own the lock cannot release it.
     * This protection must continue working after the fix.
     */
    @Test
    void testNonOwnerCannotReleaseLock() {
        String lockKey = "test:lock:non-owner-" + UUID.randomUUID();
        String lockValueA = "owner-A-" + UUID.randomUUID();
        String lockValueB = "owner-B-" + UUID.randomUUID();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires lock
        bucket.setIfAbsent(lockValueA, Duration.ofSeconds(10));
        
        // Thread B tries to release with wrong value
        String currentValue = bucket.get();
        boolean released = false;
        if (lockValueB.equals(currentValue)) {
            released = bucket.delete();
        }
        
        assertThat(released)
                .as("Non-owner should NOT release the lock")
                .isFalse();
        
        // Verify lock still exists and belongs to Thread A
        String afterAttempt = bucket.get();
        assertThat(afterAttempt)
                .as("Lock should still exist after failed release attempt")
                .isEqualTo(lockValueA);
        
        // Cleanup
        bucket.delete();
    }

    /**
     * Preservation Property 5: Lock Expiration Works
     * 
     * Verifies that locks expire after their TTL and can be re-acquired.
     * This automatic cleanup mechanism must continue working.
     */
    @Test
    void testLockExpirationWorks() throws InterruptedException {
        String lockKey = "test:lock:expiration-" + UUID.randomUUID();
        String lockValueA = "owner-A-" + UUID.randomUUID();
        String lockValueB = "owner-B-" + UUID.randomUUID();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires lock with 2-second TTL
        bucket.setIfAbsent(lockValueA, Duration.ofSeconds(2));
        
        // Verify lock exists
        assertThat(bucket.get()).isEqualTo(lockValueA);
        
        // Wait for expiration
        Thread.sleep(2500);
        
        // Verify lock expired
        String afterExpiration = bucket.get();
        assertThat(afterExpiration)
                .as("Lock should expire after TTL")
                .isNull();
        
        // Thread B can now acquire lock
        boolean acquiredB = bucket.setIfAbsent(lockValueB, Duration.ofSeconds(10));
        assertThat(acquiredB)
                .as("New thread should acquire lock after expiration")
                .isTrue();
        
        // Cleanup
        bucket.delete();
    }

    /**
     * Preservation Property 6: Sequential Lock Acquisition Works
     * 
     * Verifies that after one thread releases a lock, another thread can acquire it.
     * This sequential access pattern must continue working.
     */
    @Test
    void testSequentialLockAcquisitionWorks() {
        String lockKey = "test:lock:sequential-" + UUID.randomUUID();
        String lockValueA = "owner-A-" + UUID.randomUUID();
        String lockValueB = "owner-B-" + UUID.randomUUID();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires and releases lock
        bucket.setIfAbsent(lockValueA, Duration.ofSeconds(10));
        String currentValue = bucket.get();
        if (lockValueA.equals(currentValue)) {
            bucket.delete();
        }
        
        // Thread B acquires lock
        boolean acquiredB = bucket.setIfAbsent(lockValueB, Duration.ofSeconds(10));
        
        assertThat(acquiredB)
                .as("Second thread should acquire lock after first thread releases")
                .isTrue();
        
        assertThat(bucket.get())
                .as("Lock should belong to second thread")
                .isEqualTo(lockValueB);
        
        // Cleanup
        bucket.delete();
    }

    /**
     * Preservation Property 7: Concurrent Lock Attempts Are Serialized
     * 
     * Verifies that when multiple threads try to acquire the same lock,
     * only one succeeds and others fail gracefully.
     */
    @Test
    void testConcurrentLockAttemptsAreSerialized() throws InterruptedException {
        String lockKey = "test:lock:concurrent-" + UUID.randomUUID();
        
        AtomicBoolean thread1Acquired = new AtomicBoolean(false);
        AtomicBoolean thread2Acquired = new AtomicBoolean(false);
        AtomicBoolean thread3Acquired = new AtomicBoolean(false);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        // Three threads try to acquire same lock simultaneously
        Thread t1 = new Thread(() -> {
            try {
                startLatch.await();
                RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
                boolean acquired = bucket.setIfAbsent("thread-1", Duration.ofSeconds(10));
                thread1Acquired.set(acquired);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                startLatch.await();
                RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
                boolean acquired = bucket.setIfAbsent("thread-2", Duration.ofSeconds(10));
                thread2Acquired.set(acquired);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                startLatch.await();
                RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
                boolean acquired = bucket.setIfAbsent("thread-3", Duration.ofSeconds(10));
                thread3Acquired.set(acquired);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        t1.start();
        t2.start();
        t3.start();
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        doneLatch.await(5, TimeUnit.SECONDS);

        // Exactly one thread should have acquired the lock
        int acquiredCount = (thread1Acquired.get() ? 1 : 0) +
                           (thread2Acquired.get() ? 1 : 0) +
                           (thread3Acquired.get() ? 1 : 0);
        
        assertThat(acquiredCount)
                .as("Exactly one thread should acquire the lock")
                .isEqualTo(1);
        
        // Cleanup
        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        bucket.delete();
    }

    /**
     * Preservation Property 8: Lock Value Integrity
     * 
     * Verifies that lock values are stored and retrieved correctly without corruption.
     * This data integrity must be maintained after the fix.
     */
    @Test
    void testLockValueIntegrity() {
        String lockKey = "test:lock:integrity-" + UUID.randomUUID();
        String complexValue = "owner:" + UUID.randomUUID() + ":timestamp:" + System.currentTimeMillis();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Store complex value
        bucket.setIfAbsent(complexValue, Duration.ofSeconds(10));
        
        // Retrieve and verify
        String retrieved = bucket.get();
        
        assertThat(retrieved)
                .as("Lock value should be stored and retrieved without corruption")
                .isEqualTo(complexValue);
        
        // Cleanup
        bucket.delete();
    }
}
