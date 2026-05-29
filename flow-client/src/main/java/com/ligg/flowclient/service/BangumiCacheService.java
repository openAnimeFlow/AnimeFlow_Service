package com.ligg.flowclient.service;

import com.ligg.common.constants.bangumi.BangumiConstants;
import com.ligg.common.exception.BangumiUpstreamException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Bangumi 接口共用的 Redis 缓存击穿保护（读缓存 → 分布式锁 → 回源 → 写缓存）。
 */
@Service
@RequiredArgsConstructor
public class BangumiCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 由缓存键派生分布式锁键（{@code {cacheKey}:lock}）。
     */
    public static String lockKey(String cacheKey) {
        return cacheKey + ":lock";
    }

    /**
     * 读缓存或回源加载，锁键使用 {@link #lockKey(String)}。
     *
     * @param cacheKey           Redis 缓存键
     * @param type               缓存值类型，用于安全转型
     * @param ttlSeconds         写入缓存的过期时间（秒）
     * @param timeoutMessage     等待锁超时时的异常文案
     * @param interruptedMessage 等待被中断时的异常文案
     * @param loader             缓存未命中且持锁成功时的回源逻辑
     */
    public <T> T getOrLoad(
            String cacheKey,
            Class<T> type,
            long ttlSeconds,
            String timeoutMessage,
            String interruptedMessage,
            Supplier<T> loader) {
        return getOrLoad(cacheKey, lockKey(cacheKey), type, ttlSeconds, timeoutMessage, interruptedMessage, loader);
    }

    /**
     * 读缓存或回源加载，可指定独立锁键（如每日放送全局锁）。
     */
    public <T> T getOrLoad(
            String cacheKey,
            String lockKey,
            Class<T> type,
            long ttlSeconds,
            String timeoutMessage,
            String interruptedMessage,
            Supplier<T> loader) {
        return getOrLoad(cacheKey, lockKey, type, ttlSeconds, timeoutMessage, interruptedMessage, loader, value -> true,
                null);
    }

    /**
     * 读缓存或回源加载；命中缓存时执行 {@code onCacheHit}（如打日志）。
     */
    public <T> T getOrLoad(
            String cacheKey,
            Class<T> type,
            long ttlSeconds,
            String timeoutMessage,
            String interruptedMessage,
            Supplier<T> loader,
            Runnable onCacheHit) {
        return getOrLoad(cacheKey, lockKey(cacheKey), type, ttlSeconds, timeoutMessage, interruptedMessage, loader,
                value -> true, onCacheHit);
    }

    /**
     * 读缓存或回源加载；仅当 {@code shouldCache} 为 true 时写入 Redis。
     */
    public <T> T getOrLoad(
            String cacheKey,
            String lockKey,
            Class<T> type,
            long ttlSeconds,
            String timeoutMessage,
            String interruptedMessage,
            Supplier<T> loader,
            Predicate<T> shouldCache) {
        return getOrLoad(cacheKey, lockKey, type, ttlSeconds, timeoutMessage, interruptedMessage, loader, shouldCache,
                null);
    }

    /**
     * 完整版：读缓存 → 抢锁 → 双重检查 → {@code loader} 回源 → 按条件写缓存；未持锁则轮询直至超时。
     */
    public <T> T getOrLoad(
            String cacheKey,
            String lockKey,
            Class<T> type,
            long ttlSeconds,
            String timeoutMessage,
            String interruptedMessage,
            Supplier<T> loader,
            Predicate<T> shouldCache,
            Runnable onCacheHit) {
        long deadline = System.currentTimeMillis() + BangumiConstants.BANGUMI_CALENDAR_CACHE_WAIT_MILLIS;
        while (true) {
            T cached = getCached(cacheKey, type);
            if (cached != null) {
                if (onCacheHit != null) {
                    onCacheHit.run();
                }
                return cached;
            }

            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    lockKey,
                    "1",
                    BangumiConstants.BANGUMI_CALENDAR_LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    cached = getCached(cacheKey, type);
                    if (cached != null) {
                        if (onCacheHit != null) {
                            onCacheHit.run();
                        }
                        return cached;
                    }

                    T value = loader.get();
                    if (shouldCache.test(value)) {
                        redisTemplate.opsForValue().set(cacheKey, value, ttlSeconds, TimeUnit.SECONDS);
                    }
                    return value;
                } finally {
                    redisTemplate.delete(lockKey);
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                throw new BangumiUpstreamException(timeoutMessage);
            }
            sleep(interruptedMessage);
        }
    }

    /** 从 Redis 读取并做类型校验，类型不匹配视为未命中。 */
    @SuppressWarnings("unchecked")
    private <T> T getCached(String cacheKey, Class<T> type) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (type.isInstance(cached)) {
            return (T) cached;
        }
        return null;
    }

    /** 缓存重建等待轮询，被中断时包装为 {@link BangumiUpstreamException}。 */
    private void sleep(String interruptedMessage) {
        try {
            Thread.sleep(BangumiConstants.BANGUMI_CALENDAR_CACHE_POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BangumiUpstreamException(interruptedMessage, e);
        }
    }
}
