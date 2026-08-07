-- 原子地移除队列中的毒消息并推入死信队列，避免消息丢失或永久阻塞消费。
if redis.call('LREM', KEYS[1], 1, ARGV[1]) == 1 then
    return redis.call('RPUSH', KEYS[2], ARGV[1])
end

return 0
