-- Batch Pop Script
-- 
-- PURPOSE: Pop multiple items from Redis list atomically
-- 
-- PROBLEM: Loop-based pop with individual pollLast() calls
--   - 200 round-trips for batchSize=200
--   - High latency (200ms with 1ms network latency)
--   - Reduced throughput
--
-- SOLUTION: Lua script pops multiple items in single round-trip
--   - Single network round-trip
--   - Low latency (~1ms regardless of batch size)
--   - High throughput
--
-- INPUTS:
--   KEYS[1]: List key (e.g., "rewards:outbox")
--   ARGV[1]: Batch size (number of items to pop)
--
-- OUTPUTS:
--   Array of popped items (may be less than batch size if list exhausted)
--
-- USAGE:
--   redis.call('EVALSHA', sha, 1, 'rewards:outbox', '200')

local key = KEYS[1]
local count = tonumber(ARGV[1])
local result = {}

for i = 1, count do
    local item = redis.call('RPOP', key)
    if item == false then
        break
    end
    table.insert(result, item)
end

return result
