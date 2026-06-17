package com.ligg.flowclient.service;

import com.ligg.common.constants.bangumi.BangumiConstants;
import com.ligg.common.exception.BangumiUpstreamException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Bangumi 接口共用的 Redis 缓存击穿保护（读缓存 → 分布式锁 → 回源 → 写缓存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiCacheService {

    /**
     * 脏缓存清理后的冷却时间：期间跳过对该键的 Redis 读取，避免轮询中反复 get/delete。
     */
    private static final long CORRUPT_CACHE_COOLDOWN_MS = 30_000L;

    private final RedisTemplate<String, Object> redisTemplate;

    /** 全局记录近期已判定为脏数据的缓存键及冷却截止时间（毫秒时间戳）。 */
    private final ConcurrentHashMap<String, Long> corruptCacheUntil = new ConcurrentHashMap<>();

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
     * 读缓存或回源加载；命中缓存时执行 {@code onCacheHit}。
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
        CacheReadContext readContext = new CacheReadContext();
        while (true) {
            T cached = getCached(cacheKey, type, readContext);
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
                    cached = getCached(cacheKey, type, readContext);
                    if (cached != null) {
                        if (onCacheHit != null) {
                            onCacheHit.run();
                        }
                        return cached;
                    }

                    T value = loader.get();
                    if (shouldCache.test(value)) {
                        redisTemplate.opsForValue().set(cacheKey, value, ttlSeconds, TimeUnit.SECONDS);
                        clearCorruptCooldown(cacheKey);
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

    /**
     * 从 Redis 读取并做类型校验。
     * 反序列化失败或类型不匹配时最多清理一次，随后在本轮 {@link #getOrLoad} 内跳过读缓存，直接回源。
     */
    @SuppressWarnings("unchecked")
    private <T> T getCached(String cacheKey, Class<T> type, CacheReadContext readContext) {
        if (readContext.skipRead || isInCorruptCooldown(cacheKey)) {
            readContext.skipRead = true;
            return null;
        }
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return null;
            }
            if (type.isInstance(cached)) {
                clearCorruptCooldown(cacheKey);
                return (T) cached;
            }
            log.warn("缓存类型不匹配，已清理 cacheKey={}, expected={}, actual={}",
                    cacheKey, type.getName(), cached.getClass().getName());
            evictCorruptCache(cacheKey, readContext, "type mismatch");
            return null;
        } catch (RuntimeException e) {
            if (isCacheDeserializationFailure(e)) {
                evictCorruptCache(cacheKey, readContext, e.getMessage());
                return null;
            }
            throw e;
        }
    }

    /**
     * 每个 {@link #getOrLoad} 调用独享：避免同一次等待循环内对同一 key 反复 get/delete。
     */
    private static final class CacheReadContext {
        private boolean skipRead;
        private boolean evicted;
    }

    private void evictCorruptCache(String cacheKey, CacheReadContext readContext, String reason) {
        readContext.skipRead = true;
        if (readContext.evicted) {
            return;
        }
        readContext.evicted = true;
        corruptCacheUntil.put(cacheKey, System.currentTimeMillis() + CORRUPT_CACHE_COOLDOWN_MS);
        log.warn("缓存数据不可用，已清理脏数据 cacheKey={}, reason={}", cacheKey, reason);
        safeDelete(cacheKey);
    }

    private boolean isInCorruptCooldown(String cacheKey) {
        Long until = corruptCacheUntil.get(cacheKey);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() < until) {
            return true;
        }
        corruptCacheUntil.remove(cacheKey, until);
        return false;
    }

    private void clearCorruptCooldown(String cacheKey) {
        corruptCacheUntil.remove(cacheKey);
    }

    private void safeDelete(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException deleteEx) {
            log.warn("清理脏缓存失败 cacheKey={}, message={}", cacheKey, deleteEx.getMessage());
        }
    }

    private static boolean isCacheDeserializationFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SerializationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null
                    && (message.contains("Could not read JSON")
                    || message.contains("Could not resolve type id")
                    || message.contains("Unexpected token")
                    || message.contains("Cannot deserialize"))) {
                return true;
            }
        }
        return false;
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
