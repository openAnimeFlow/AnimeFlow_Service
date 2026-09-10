-- All comparisons and writes run atomically. Values use the application's serializer.
local current = redis.call('GET', KEYS[1])
if not current then return nil end
local session = cjson.decode(current)
if session[2] then session = session[2] end
local replay = redis.call('GET', KEYS[2])
if replay and redis.call('GET', KEYS[3]) == session.refreshJti then
    return replay
end
if current ~= ARGV[1] then return '' end
if session.refreshJti ~= ARGV[4] then return nil end
redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[8])
redis.call('SET', KEYS[4], ARGV[6], 'EX', ARGV[7])
redis.call('SET', KEYS[5], ARGV[6], 'EX', ARGV[8])
-- Keep the previous access index briefly for requests already in flight.
local accessTtl = redis.call('PTTL', KEYS[6])
local graceMs = tonumber(ARGV[9]) * 1000
if accessTtl > graceMs then redis.call('PEXPIRE', KEYS[6], graceMs) end
redis.call('DEL', KEYS[7])
redis.call('SADD', KEYS[8], ARGV[6])
if redis.call('TTL', KEYS[8]) < tonumber(ARGV[8]) then
    redis.call('EXPIRE', KEYS[8], ARGV[8])
end
redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[9])
redis.call('SET', KEYS[3], ARGV[5], 'EX', ARGV[9])
return ARGV[3]
