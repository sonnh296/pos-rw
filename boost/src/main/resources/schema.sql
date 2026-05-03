CREATE TABLE IF NOT EXISTS reward_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(128) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    points_delta BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_reward_ledger_txn (transaction_id),
    KEY idx_reward_ledger_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_balance (
    customer_id VARCHAR(64) NOT NULL PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS test_phase1_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    executor_type VARCHAR(32) NOT NULL,
    lock_mode VARCHAR(32) NOT NULL,
    iteration INT NOT NULL,
    amounts VARCHAR(255),
    expected_val BIGINT,
    actual_val BIGINT,
    is_accurate BOOLEAN,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS test_phase2_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    executor_type VARCHAR(32) NOT NULL,
    iteration INT NOT NULL,
    duration_ms BIGINT,
    throughput_rps DOUBLE,
    p95_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
