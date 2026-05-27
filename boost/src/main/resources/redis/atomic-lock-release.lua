-- Atomic Lock Release Script
-- 
-- PURPOSE: Atomically check lock ownership and delete if owned
-- 
-- PROBLEM: Non-atomic GET-then-DELETE allows race conditions:
--   1. Thread A: GET lock → returns "uuid-A"
--   2. Lock expires or Thread B acquires lock with "uuid-B"
--   3. Thread A: DELETE lock → deletes Thread B's lock!
--   4. Result: Both threads operate without mutual exclusion
--
-- SOLUTION: Lua script executes atomically on Redis server
--   - Check ownership and delete in single atomic operation
--   - No other commands can interleave
--   - Prevents race condition completely
--
-- INPUTS:
--   KEYS[1]: Lock key (e.g., "lock:customer:123")
--   ARGV[1]: Expected lock value (owner's UUID)
--
-- OUTPUTS:
--   1 = Lock was owned by caller and successfully deleted
--   0 = Lock not owned by caller (different value or doesn't exist)
--
-- USAGE:
--   redis.call('EVALSHA', sha, 1, 'lock:customer:123', 'uuid-A')
--
-- GUARANTEES:
--   - Atomicity: Check and delete happen together, no interleaving
--   - Ownership: Only the lock owner can delete their lock
--   - Safety: Cannot delete another thread's lock

local lockKey = KEYS[1]
local expectedValue = ARGV[1]

-- Get current lock value
local currentValue = redis.call('GET', lockKey)

-- If lock doesn't exist or belongs to someone else, return 0
if currentValue == false or currentValue ~= expectedValue then
    return 0
end

-- Lock belongs to us, delete it atomically
redis.call('DEL', lockKey)
return 1
