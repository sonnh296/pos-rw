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
import turbo.pos.boost.dto.RewardResponse;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.repository.RewardRepository;
import turbo.pos.boost.service.MysqlNoLockRewardService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MysqlNoLockRewardService hoạt động đúng trong môi trường dev/test (không phải production).
 */
@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "app.rewards.allow-no-lock=true"
})
@Testcontainers
public class MysqlNoLockPreservationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MysqlNoLockRewardService service;

    @Autowired
    private RewardRepository repository;

    /**
     * Preservation Property 1: Service Processes Rewards Correctly in Test Environment
     * 
     * Verifies that MysqlNoLockRewardService successfully processes reward transactions
     * in a test environment with single-threaded access.
     * 
     * This is the core functionality that must continue working after adding the production guard.
     */
    @Test
    void serviceProcessesRewardsCorrectlyInTestEnvironment() {
        // Test with multiple scenarios
        for (int i = 0; i < 20; i++) {
            String customerId = "customer-" + UUID.randomUUID();
            int amount = 100 + (i * 50);
            String txnId = "txn-" + UUID.randomUUID();
            
            // Create transaction request
            TransactionRequest request = TransactionRequest.builder()
                    .customerId(customerId)
                    .transactionId(txnId)
                    .amount(amount)
                    .build();
            
            // Process reward
            RewardResponse response = service.processReward(request);
            
            // Verify successful processing
            assertThat(response.getStatus())
                    .as("Service should successfully process reward in test environment")
                    .isEqualTo("SUCCESS");
            
            assertThat(response.getCustomerId())
                    .as("Response should contain correct customer ID")
                    .isEqualTo(customerId);
            
            assertThat(response.getTotalPoints())
                    .as("Response should contain positive points balance")
                    .isGreaterThan(0L);
            
            // Verify transaction was recorded in ledger
            boolean exists = repository.existsByTransactionId(txnId);
            assertThat(exists)
                    .as("Transaction should be recorded in ledger")
                    .isTrue();
        }
    }

    /**
     * Preservation Property 2: Balance Calculations Are Accurate
     * 
     * Verifies that the service correctly calculates and updates customer balances
     * based on transaction amounts.
     */
    @Test
    void balanceCalculationsAreAccurate() {
        for (int i = 0; i < 15; i++) {
            String uniqueCustomerId = "customer-balance-" + UUID.randomUUID();
            int amount1 = 100 + (i * 20);
            int amount2 = 200 + (i * 30);
            
            // Process first transaction
            String txnId1 = "txn-" + UUID.randomUUID();
            TransactionRequest request1 = TransactionRequest.builder()
                    .customerId(uniqueCustomerId)
                    .transactionId(txnId1)
                    .amount(amount1)
                    .build();
            
            RewardResponse response1 = service.processReward(request1);
            assertThat(response1.getStatus()).isEqualTo("SUCCESS");
            long balanceAfterFirst = response1.getTotalPoints();
            
            // Process second transaction
            String txnId2 = "txn-" + UUID.randomUUID();
            TransactionRequest request2 = TransactionRequest.builder()
                    .customerId(uniqueCustomerId)
                    .transactionId(txnId2)
                    .amount(amount2)
                    .build();
            
            RewardResponse response2 = service.processReward(request2);
            assertThat(response2.getStatus()).isEqualTo("SUCCESS");
            long balanceAfterSecond = response2.getTotalPoints();
            
            // Verify balance increased correctly
            // Points calculation: amount * 10 (based on RewardUtils.calculatePoints)
            long expectedIncrease = Math.round(amount2 * 10.0);
            long actualIncrease = balanceAfterSecond - balanceAfterFirst;
            
            assertThat(actualIncrease)
                    .as("Balance should increase by correct amount after second transaction")
                    .isEqualTo(expectedIncrease);
            
            assertThat(balanceAfterSecond)
                    .as("Final balance should be greater than initial balance")
                    .isGreaterThan(balanceAfterFirst);
        }
    }

    /**
     * Preservation Property 3: Idempotency Works Correctly
     * 
     * Verifies that duplicate transaction IDs are correctly rejected,
     * preventing double-processing of the same transaction.
     */
    @Test
    void idempotencyWorksCorrectly() {
        for (int i = 0; i < 15; i++) {
            String uniqueCustomerId = "customer-idem-" + UUID.randomUUID();
            String txnId = "txn-" + UUID.randomUUID();
            int amount = 100 + (i * 100);
            
            // Process transaction first time
            TransactionRequest request = TransactionRequest.builder()
                    .customerId(uniqueCustomerId)
                    .transactionId(txnId)
                    .amount(amount)
                    .build();
            
            RewardResponse response1 = service.processReward(request);
            assertThat(response1.getStatus()).isEqualTo("SUCCESS");
            long balanceAfterFirst = response1.getTotalPoints();
            
            // Process same transaction again (duplicate)
            RewardResponse response2 = service.processReward(request);
            
            // Verify duplicate is rejected
            assertThat(response2.getStatus())
                    .as("Duplicate transaction should be rejected")
                    .isEqualTo("DUPLICATE_TRANSACTION");
            
            // Verify balance unchanged
            assertThat(response2.getTotalPoints())
                    .as("Balance should not change for duplicate transaction")
                    .isEqualTo(balanceAfterFirst);
            
            // Verify transaction still exists (idempotency maintained)
            boolean stillExists = repository.existsByTransactionId(txnId);
            assertThat(stillExists)
                    .as("Transaction should still exist in ledger after duplicate attempt")
                    .isTrue();
        }
    }

    /**
     * Preservation Property 4: Service Handles Multiple Customers Correctly
     * 
     * Verifies that the service correctly isolates transactions for different customers,
     * maintaining separate balances.
     */
    @Test
    void serviceHandlesMultipleCustomersCorrectly() {
        for (int i = 0; i < 10; i++) {
            String uniqueCustomer1 = "customer-multi-1-" + UUID.randomUUID();
            String uniqueCustomer2 = "customer-multi-2-" + UUID.randomUUID();
            int amount1 = 100 + (i * 50);
            int amount2 = 200 + (i * 75);
            
            // Process transaction for customer 1
            String txnId1 = "txn-" + UUID.randomUUID();
            TransactionRequest request1 = TransactionRequest.builder()
                    .customerId(uniqueCustomer1)
                    .transactionId(txnId1)
                    .amount(amount1)
                    .build();
            
            RewardResponse response1 = service.processReward(request1);
            assertThat(response1.getStatus()).isEqualTo("SUCCESS");
            
            // Process transaction for customer 2
            String txnId2 = "txn-" + UUID.randomUUID();
            TransactionRequest request2 = TransactionRequest.builder()
                    .customerId(uniqueCustomer2)
                    .transactionId(txnId2)
                    .amount(amount2)
                    .build();
            
            RewardResponse response2 = service.processReward(request2);
            assertThat(response2.getStatus()).isEqualTo("SUCCESS");
            
            // Verify customers have independent balances
            assertThat(response1.getCustomerId()).isEqualTo(uniqueCustomer1);
            assertThat(response2.getCustomerId()).isEqualTo(uniqueCustomer2);
            
            // Verify balances are calculated independently
            long expectedPoints1 = Math.round(amount1 * 10.0);
            long expectedPoints2 = Math.round(amount2 * 10.0);
            
            assertThat(response1.getTotalPoints())
                    .as("Customer 1 balance should match their transaction")
                    .isEqualTo(expectedPoints1);
            
            assertThat(response2.getTotalPoints())
                    .as("Customer 2 balance should match their transaction")
                    .isEqualTo(expectedPoints2);
        }
    }

    /**
     * Preservation Property 5: Service Works in Test Profile
     * 
     * Verifies that the service can be activated and works correctly
     * when spring.profiles.active=test (non-production environment).
     */
    @Test
    void serviceWorksInTestProfile() {
        // This test runs with spring.profiles.active=test (set in @SpringBootTest)
        // The service should be active and functional
        
        for (int i = 0; i < 10; i++) {
            String uniqueCustomerId = "customer-profile-" + UUID.randomUUID();
            String txnId = "txn-" + UUID.randomUUID();
            int amount = 100 + (i * 100);
            
            TransactionRequest request = TransactionRequest.builder()
                    .customerId(uniqueCustomerId)
                    .transactionId(txnId)
                    .amount(amount)
                    .build();
            
            RewardResponse response = service.processReward(request);
            
            // Verify service is functional in test profile
            assertThat(response)
                    .as("Service should return response in test profile")
                    .isNotNull();
            
            assertThat(response.getStatus())
                    .as("Service should successfully process in test profile")
                    .isEqualTo("SUCCESS");
            
            assertThat(response.getTotalPoints())
                    .as("Service should calculate points correctly in test profile")
                    .isEqualTo(Math.round(amount * 10.0));
        }
    }

    /**
     * Preservation Property 6: Sequential Transactions Accumulate Points
     * 
     * Verifies that multiple sequential transactions for the same customer
     * correctly accumulate points in the balance.
     */
    @Test
    void sequentialTransactionsAccumulatePoints() {
        for (int testRun = 0; testRun < 10; testRun++) {
            String uniqueCustomerId = "customer-seq-" + UUID.randomUUID();
            long expectedTotalPoints = 0;
            int transactionCount = 3 + (testRun % 3);
            int baseAmount = 100 + (testRun * 50);
            
            // Process multiple transactions sequentially
            for (int i = 0; i < transactionCount; i++) {
                String txnId = "txn-" + UUID.randomUUID();
                int amount = baseAmount * (i + 1);
                
                TransactionRequest request = TransactionRequest.builder()
                        .customerId(uniqueCustomerId)
                        .transactionId(txnId)
                        .amount(amount)
                        .build();
                
                RewardResponse response = service.processReward(request);
                
                assertThat(response.getStatus())
                        .as("Transaction %d should succeed", i + 1)
                        .isEqualTo("SUCCESS");
                
                expectedTotalPoints += Math.round(amount * 10.0);
                
                assertThat(response.getTotalPoints())
                        .as("Balance after transaction %d should match accumulated points", i + 1)
                        .isEqualTo(expectedTotalPoints);
            }
        }
    }

    /**
     * Preservation Property 7: Service Returns Correct Metadata
     * 
     * Verifies that the service returns correct metadata in the response,
     * including thread name and processing time.
     */
    @Test
    void serviceReturnsCorrectMetadata() {
        for (int i = 0; i < 10; i++) {
            String uniqueCustomerId = "customer-meta-" + UUID.randomUUID();
            String txnId = "txn-" + UUID.randomUUID();
            int amount = 100 + (i * 100);
            
            TransactionRequest request = TransactionRequest.builder()
                    .customerId(uniqueCustomerId)
                    .transactionId(txnId)
                    .amount(amount)
                    .build();
            
            RewardResponse response = service.processReward(request);
            
            // Verify metadata is present
            assertThat(response.getThreadName())
                    .as("Response should include thread name")
                    .isNotNull()
                    .isNotBlank();
            
            assertThat(response.getProcessingTimeMs())
                    .as("Response should include processing time")
                    .isGreaterThanOrEqualTo(0L);
            
            assertThat(response.getCustomerId())
                    .as("Response should include customer ID")
                    .isEqualTo(uniqueCustomerId);
        }
    }

    /**
     * Preservation Property 8: Service Handles Small Amounts
     * 
     * Verifies that the service correctly handles edge cases like
     * small transaction amounts that result in minimal points.
     */
    @Test
    void serviceHandlesSmallAmounts() {
        for (int smallAmount = 1; smallAmount <= 9; smallAmount++) {
            String uniqueCustomerId = "customer-small-" + UUID.randomUUID();
            String txnId = "txn-" + UUID.randomUUID();
            
            TransactionRequest request = TransactionRequest.builder()
                    .customerId(uniqueCustomerId)
                    .transactionId(txnId)
                    .amount(smallAmount)
                    .build();
            
            RewardResponse response = service.processReward(request);
            
            // Verify service handles small amounts gracefully
            assertThat(response.getStatus())
                    .as("Service should process small amounts successfully")
                    .isEqualTo("SUCCESS");
            
            // Points calculation: amount * 10
            long expectedPoints = Math.round(smallAmount * 10.0);
            
            assertThat(response.getTotalPoints())
                    .as("Service should calculate correct points for small amounts")
                    .isEqualTo(expectedPoints);
        }
    }
}
