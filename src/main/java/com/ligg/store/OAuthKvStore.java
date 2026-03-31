package com.ligg.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 流程用的进程内 KV 存储（带过期时间），替代 Redis。
 */
@Component
public class OAuthKvStore {

    private record Hold(Object value, long expireAtNanos) {}

    private final ConcurrentHashMap<String, Hold> map = new ConcurrentHashMap<>();

    public void put(String key, Object value, long ttlSeconds) {
        long ttl = Math.max(1L, ttlSeconds);
        map.put(key, new Hold(value, System.nanoTime() + TimeUnit.SECONDS.toNanos(ttl)));
    }

    public Object get(String key) {
        Hold h = map.get(key);
        if (h == null) {
            return null;
        }
        if (System.nanoTime() > h.expireAtNanos()) {
            map.remove(key, h);
            return null;
        }
        return h.value();
    }

    public void remove(String key) {
        map.remove(key);
    }
}
