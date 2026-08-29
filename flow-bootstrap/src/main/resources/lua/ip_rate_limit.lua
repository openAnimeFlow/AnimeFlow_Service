-- 固定窗口计数限流：KEYS[1] 为 Redis 键；ARGV[1] 为窗口内最大请求数；ARGV[2] 为窗口长度（秒）
-- 返回 1 表示允许，0 表示超过限制
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end
local limit = tonumber(ARGV[1])
if current > limit then
    return 0
end
return 1
