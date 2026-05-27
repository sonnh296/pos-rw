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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** GET-then-DELETE không atomic: thread A có thể xóa lock của thread B. */
@SpringBootTest
@Testcontainers
public class VirtualThreadLockReleaseRaceConditionTest {

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
     * Mô phỏng GET-then-DELETE: thread A xóa nhầm lock của thread B.
     */
    @Test
    void testNonAtomicLockReleaseRaceCondition() throws Exception {
        String lockKey = "test:lock:customer-" + UUID.randomUUID();
        String lockValueA = "uuid-A-" + UUID.randomUUID();
        String lockValueB = "uuid-B-" + UUID.randomUUID();

        AtomicBoolean threadADeletedThreadBLock = new AtomicBoolean(false);
        AtomicReference<String> lockValueAfterThreadARelease = new AtomicReference<>();
        
        CountDownLatch threadAGetCompleted = new CountDownLatch(1);
        CountDownLatch threadBAcquiredLock = new CountDownLatch(1);
        CountDownLatch threadADeleteCompleted = new CountDownLatch(1);

        // Thread A: Acquires lock, then releases with non-atomic GET-DELETE
        Thread threadA = new Thread(() -> {
            try {
                // Thread A acquires lock
                RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
                bucket.set(lockValueA, 5, TimeUnit.SECONDS);
                
                // Simulate processing
                Thread.sleep(100);
                
                // Start non-atomic release: GET operation
                String currentValue = bucket.get();
                threadAGetCompleted.countDown();
                
                // Wait for Thread B to acquire lock (simulating race condition window)
                threadBAcquiredLock.await(2, TimeUnit.SECONDS);
                
                // Complete non-atomic release: DELETE operation
                if (lockValueA.equals(currentValue)) {
                    bucket.delete();
                    
                    // Check if we deleted Thread B's lock
                    String valueAfterDelete = bucket.get();
                    lockValueAfterThreadARelease.set(valueAfterDelete);
                    
                    if (valueAfterDelete == null) {
                        // We deleted the lock, but was it Thread B's lock?
                        threadADeletedThreadBLock.set(true);
                    }
                }
                
                threadADeleteCompleted.countDown();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Thread B: Acquires lock after Thread A's GET but before Thread A's DELETE
        Thread threadB = new Thread(() -> {
            try {
                // Wait for Thread A to complete GET operation
                threadAGetCompleted.await(2, TimeUnit.SECONDS);
                
                // Small delay to ensure we're in the race condition window
                Thread.sleep(50);
                
                // Thread B acquires lock (simulating lock expiration or Thread A already deleted it)
                RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
                
                // Force acquire by setting new value (simulating lock expiration scenario)
                bucket.set(lockValueB, 5, TimeUnit.SECONDS);
                
                threadBAcquiredLock.countDown();
                
                // Wait for Thread A to complete DELETE
                threadADeleteCompleted.await(2, TimeUnit.SECONDS);
                
                // Check if our lock still exists
                String currentValue = bucket.get();
                
                // If currentValue is null, Thread A deleted our lock!
                if (currentValue == null) {
                    System.out.println("RACE CONDITION DETECTED: Thread A deleted Thread B's lock!");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        threadA.start();
        threadB.start();

        threadA.join(5000);
        threadB.join(5000);

        RBucket<String> finalBucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        String finalLockValue = finalBucket.get();

        System.out.println("Thread A deleted Thread B's lock: " + threadADeletedThreadBLock.get());
        System.out.println("Lock value after Thread A release: " + lockValueAfterThreadARelease.get());
        System.out.println("Final lock value: " + finalLockValue);

        assertThat(threadADeletedThreadBLock.get())
                .as("Non-atomic release should delete Thread B's lock")
                .isTrue();
        assertThat(finalLockValue).isNull();
    }

    /** GET-then-DELETE đơn giản: thread A xóa lock của thread B. */
    @Test
    void testLockReleaseDeletesAnotherThreadsLock() throws Exception {
        String lockKey = "test:lock:simple-" + UUID.randomUUID();
        
        RBucket<String> bucket = redissonClient.getBucket(lockKey, StringCodec.INSTANCE);
        
        // Thread A acquires lock
        String lockValueA = "thread-A";
        bucket.set(lockValueA, 10, TimeUnit.SECONDS);
        
        // Thread A starts release: GET
        String currentValue = bucket.get();
        assertThat(currentValue).isEqualTo(lockValueA);
        
        // Simulate race condition: Thread B acquires lock before Thread A's DELETE
        String lockValueB = "thread-B";
        bucket.set(lockValueB, 10, TimeUnit.SECONDS);
        
        // Thread A completes release: DELETE (but now it's Thread B's lock!)
        if (lockValueA.equals(currentValue)) {
            bucket.delete();
        }
        
        // Verify: Thread B's lock was incorrectly deleted
        String finalValue = bucket.get();

        System.out.println("Expected: " + lockValueB);
        System.out.println("Actual: " + finalValue);

        assertThat(finalValue)
                .as("GET-then-DELETE incorrectly removed Thread B's lock")
                .isNull();
    }
}
