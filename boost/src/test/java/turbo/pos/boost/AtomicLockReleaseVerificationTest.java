package turbo.pos.boost;

import org.junit.jupiter.api.Test;
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
import turbo.pos.boost.redis.AtomicLockReleaseLuaExecutor;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Atomic lock release: chỉ owner mới xóa được lock của mình. */
@SpringBootTest
@Testcontainers
public class AtomicLockReleaseVerificationTest {

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

    @Autowired
    private AtomicLockReleaseLuaExecutor atomicLockReleaseLuaExecutor;

    /**
     * Verification Test 1: Atomic Release Prevents Race Condition
     * 
     * Verifies that using the atomic Lua script prevents Thread A from deleting Thread B's lock
     */
    @Test
    void testAtomicReleasePreventsDeletingAnotherThreadsLock() {
        String lockKey = "test:lock:atomic-" + UUID.randomUUID();
        String lockValueA = "thread-A";
        String lockValueB = "thread-B";

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires lock
        bucket.setIfAbsent(lockValueA, Duration.ofSeconds(10));
        
        // Simulate race condition: Thread B acquires lock before Thread A's release
        bucket.set(lockValueB, Duration.ofSeconds(10));
        
        // Thread A tries to release using atomic Lua script
        boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValueA);
        
        // Verify: Thread A should NOT release Thread B's lock
        assertThat(released)
                .as("Thread A should NOT release lock (it doesn't own it anymore)")
                .isFalse();
        
        // Verify: Thread B's lock should still exist
        String finalValue = bucket.get();
        assertThat(finalValue)
                .as("Thread B's lock should still exist")
                .isEqualTo(lockValueB);
        
        // Cleanup
        bucket.delete();
    }

    /**
     * Verification Test 2: Owner Can Release Their Own Lock
     * 
     * Verifies that the lock owner can successfully release their lock
     */
    @Test
    void testOwnerCanReleaseOwnLock() {
        String lockKey = "test:lock:owner-" + UUID.randomUUID();
        String lockValue = "owner-" + UUID.randomUUID();

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Acquire lock
        bucket.setIfAbsent(lockValue, Duration.ofSeconds(10));
        
        // Release using atomic Lua script
        boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValue);
        
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
     * Verification Test 3: Non-Owner Cannot Release Lock
     * 
     * Verifies that a thread that doesn't own the lock cannot release it
     */
    @Test
    void testNonOwnerCannotReleaseLock() {
        String lockKey = "test:lock:non-owner-" + UUID.randomUUID();
        String lockValueA = "owner-A";
        String lockValueB = "owner-B";

        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires lock
        bucket.setIfAbsent(lockValueA, Duration.ofSeconds(10));
        
        // Thread B tries to release with wrong value
        boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValueB);
        
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
     * Verification Test 4: Concurrent Release Attempts Are Safe
     * 
     * Verifies that when multiple threads try to release the same lock,
     * only the owner succeeds and others fail gracefully
     */
    @Test
    void testConcurrentReleaseAttemptsAreSafe() throws InterruptedException {
        String lockKey = "test:lock:concurrent-release-" + UUID.randomUUID();
        String lockValueA = "thread-A";
        String lockValueB = "thread-B";
        String lockValueC = "thread-C";
        
        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires lock
        bucket.setIfAbsent(lockValueA, Duration.ofSeconds(10));
        
        AtomicBoolean threadAReleased = new AtomicBoolean(false);
        AtomicBoolean threadBReleased = new AtomicBoolean(false);
        AtomicBoolean threadCReleased = new AtomicBoolean(false);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        // Three threads try to release simultaneously
        Thread t1 = new Thread(() -> {
            try {
                startLatch.await();
                boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValueA);
                threadAReleased.set(released);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                startLatch.await();
                boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValueB);
                threadBReleased.set(released);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                startLatch.await();
                boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValueC);
                threadCReleased.set(released);
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

        // Only Thread A (the owner) should have released the lock
        assertThat(threadAReleased.get())
                .as("Thread A (owner) should release the lock")
                .isTrue();
        
        assertThat(threadBReleased.get())
                .as("Thread B (non-owner) should NOT release the lock")
                .isFalse();
        
        assertThat(threadCReleased.get())
                .as("Thread C (non-owner) should NOT release the lock")
                .isFalse();
        
        // Verify lock is gone
        String finalValue = bucket.get();
        assertThat(finalValue)
                .as("Lock should be removed after Thread A released it")
                .isNull();
    }

    /**
     * Verification Test 5: Release Non-Existent Lock Is Safe
     * 
     * Verifies that trying to release a non-existent lock doesn't cause errors
     */
    @Test
    void testReleaseNonExistentLockIsSafe() {
        String lockKey = "test:lock:non-existent-" + UUID.randomUUID();
        String lockValue = "some-value";

        // Try to release a lock that doesn't exist
        boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValue);
        
        assertThat(released)
                .as("Releasing non-existent lock should return false")
                .isFalse();
    }

    /**
     * Verification Test 6: Race Condition Scenario With Atomic Release
     * 
     * Simulates the exact race condition scenario but with atomic release
     * Verifies that Thread B's lock is NOT deleted
     */
    @Test
    void testRaceConditionScenarioWithAtomicRelease() throws InterruptedException {
        String lockKey = "test:lock:race-scenario-" + UUID.randomUUID();
        String lockValueA = "uuid-A-" + UUID.randomUUID();
        String lockValueB = "uuid-B-" + UUID.randomUUID();

        AtomicBoolean threadADeletedThreadBLock = new AtomicBoolean(false);
        
        CountDownLatch threadAAcquired = new CountDownLatch(1);
        CountDownLatch threadBAcquired = new CountDownLatch(1);
        CountDownLatch threadAReleaseCompleted = new CountDownLatch(1);

        // Thread A: Acquires lock, then releases with atomic Lua script
        Thread threadA = new Thread(() -> {
            try {
                RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
                
                // Thread A acquires lock
                bucket.setIfAbsent(lockValueA, Duration.ofSeconds(5));
                threadAAcquired.countDown();
                
                // Wait for Thread B to acquire lock (simulating race condition window)
                threadBAcquired.await(2, TimeUnit.SECONDS);
                
                // Thread A tries to release using atomic Lua script
                boolean released = atomicLockReleaseLuaExecutor.releaseLock(lockKey, lockValueA);
                
                if (!released) {
                    // Good! We didn't delete Thread B's lock
                    threadADeletedThreadBLock.set(false);
                } else {
                    // Bad! We somehow deleted Thread B's lock
                    threadADeletedThreadBLock.set(true);
                }
                
                threadAReleaseCompleted.countDown();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Thread B: Acquires lock after Thread A
        Thread threadB = new Thread(() -> {
            try {
                // Wait for Thread A to acquire lock first
                threadAAcquired.await(2, TimeUnit.SECONDS);
                
                // Small delay
                Thread.sleep(50);
                
                // Thread B acquires lock (simulating lock expiration or force acquire)
                RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
                bucket.set(lockValueB, Duration.ofSeconds(5));
                
                threadBAcquired.countDown();
                
                // Wait for Thread A to complete release
                threadAReleaseCompleted.await(2, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        threadA.start();
        threadB.start();

        threadA.join(5000);
        threadB.join(5000);

        // Verify Thread A did NOT delete Thread B's lock
        assertThat(threadADeletedThreadBLock.get())
                .as("Thread A should NOT delete Thread B's lock (atomic release prevents this)")
                .isFalse();
        
        // Verify Thread B's lock still exists
        RBucket<String> finalBucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        String finalLockValue = finalBucket.get();
        
        assertThat(finalLockValue)
                .as("Thread B's lock should still exist")
                .isEqualTo(lockValueB);
        
        // Cleanup
        finalBucket.delete();
    }
}
