-- 仅由持有当前令牌的实例释放分布式锁，防止锁过期后误删其他实例的新锁。
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end

return 0
