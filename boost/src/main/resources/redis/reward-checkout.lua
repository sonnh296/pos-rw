-- Atomic idempotency + expected counter + points hash + outbox (after Redisson lock in Java).
-- KEYS[1] idempotency key, KEYS[2] customer:points hash, KEYS[3] rewards:outbox list, KEYS[4] expected:{customerId}
-- ARGV[1] customerId field, ARGV[2] pointsDelta, ARGV[3] outboxJson, ARGV[4] idemTtlSeconds
local idem = redis.call('SET', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[4]))
if not idem then
  local current = redis.call('HGET', KEYS[2], ARGV[1])
  if not current then
    current = '0'
  end
  return {0, 'DUPLICATE', current}
end
redis.call('INCRBY', KEYS[4], ARGV[2])
local newPoints = redis.call('HINCRBY', KEYS[2], ARGV[1], ARGV[2])
redis.call('LPUSH', KEYS[3], ARGV[3])
return {1, 'SUCCESS', tostring(newPoints)}
