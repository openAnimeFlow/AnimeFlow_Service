package com.ligg.flowscheduler.background;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BackgroundEntity;
import com.ligg.common.utils.Utils;
import com.ligg.flowscheduler.config.BackgroundImageSyncProperties;
import com.ligg.flowscheduler.mapper.BackgroundImageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 背景图片同步服务：从 animeFlow-assets 拉取 background-image/index.json，
 * 解析图片 URL 列表，全量替换 background 表数据。
 *
 * @author Ligg
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundImageSyncService {

    private final BackgroundImageSyncProperties properties;
    private final BackgroundImageMapper backgroundImageMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Async("backgroundImageSyncExecutor")
    public void triggerSyncAsync() {
        try {
            syncIfNeeded();
        } catch (Exception e) {
            log.error("Background image sync failed", e);
        }
    }

    public void syncIfNeeded() throws Exception {
        if (!properties.isEnabled()) {
            log.debug("Background image sync is disabled");
            return;
        }

        // 1. 拉取远程 index.json
        String json = fetchIndexJson();
        JsonNode root = objectMapper.readTree(json);

        // 2. 读取 lastUpdated 比对 Redis
        String remoteUpdatedAt = root.has("lastUpdated")
                ? root.get("lastUpdated").asText()
                : null;
        if (remoteUpdatedAt == null || remoteUpdatedAt.isBlank()) {
            log.warn("background-image/index.json missing lastUpdated, skip sync");
            return;
        }

        Object cached = redisTemplate.opsForValue()
                .get(Constants.BACKGROUND_IMAGE_SYNC_UPDATED_AT_KEY);
        if (cached != null && remoteUpdatedAt.equals(String.valueOf(cached))) {
            log.info("Background image index is up to date: {}", remoteUpdatedAt);
            return;
        }

        // 3. 分布式锁
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                Constants.BACKGROUND_IMAGE_SYNC_LOCK_KEY,
                remoteUpdatedAt,
                Duration.ofHours(1));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("Background image sync already running, skip");
            return;
        }

        try {
            log.info("Starting background image sync, lastUpdated={}", remoteUpdatedAt);

            // 4. 解析 images 数组
            JsonNode images = root.get("images");
            if (images == null || !images.isArray() || images.isEmpty()) {
                log.warn("background-image/index.json has no images, skip sync");
                return;
            }

            List<BackgroundEntity> entities = new ArrayList<>();
            List<String> remoteNames = new ArrayList<>();
            for (JsonNode img : images) {
                String url = img.has("url") ? img.get("url").asText() : null;
                String name = img.has("name") ? img.get("name").asText() : null;
                if (url == null || url.isBlank() || name == null || name.isBlank()) {
                    continue;
                }
                BackgroundEntity entity = new BackgroundEntity();
                entity.setImage(Utils.imgUrlToWsrvCdn(url));
                entity.setName(name);
                entities.add(entity);
                remoteNames.add(name);
            }

            if (entities.isEmpty()) {
                log.warn("No valid image URLs found, skip sync");
                return;
            }

            // 5. 按需 upsert：name 已存在则更新 image，不存在则插入
            backgroundImageMapper.upsertBatch(entities);

            // 6. 清理资源仓库已删除的图片
            backgroundImageMapper.deleteByNameNotIn(remoteNames);
            log.info("Cleaned up images no longer in remote index");

            // 7. 更新 Redis 时间戳
            redisTemplate.opsForValue().set(
                    Constants.BACKGROUND_IMAGE_SYNC_UPDATED_AT_KEY,
                    remoteUpdatedAt);

            log.info("Background image sync completed, synced {} images (lastUpdated={})",
                    entities.size(), remoteUpdatedAt);
        } finally {
            redisTemplate.delete(Constants.BACKGROUND_IMAGE_SYNC_LOCK_KEY);
        }
    }

    private String fetchIndexJson() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getIndexUrl()))
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to fetch index.json, HTTP " + response.statusCode());
        }

        return response.body();
    }
}
