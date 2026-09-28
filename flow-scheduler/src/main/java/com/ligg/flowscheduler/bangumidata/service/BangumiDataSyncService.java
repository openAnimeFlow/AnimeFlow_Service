package com.ligg.flowscheduler.bangumidata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.flowscheduler.bangumidata.config.BangumiDataSyncProperties;
import com.ligg.flowscheduler.bangumidata.mapper.BangumiDataMapper;
import com.ligg.flowscheduler.bangumidata.model.BangumiDataItem;
import com.ligg.flowscheduler.bangumidata.model.BangumiDataSiteMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** 从 bangumi-data 发布产物同步番组与站点元数据到本地数据库。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiDataSyncService {

    private final BangumiDataSyncProperties properties;
    private final BangumiDataMapper mapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Async("bangumiDataSyncExecutor")
    public void triggerSyncAsync() {
        try {
            syncIfNeeded();
        } catch (Exception e) {
            log.error("bangumi-data sync failed", e);
        }
    }

    public void syncIfNeeded() throws Exception {
        if (!properties.isEnabled()) return;

        String source = fetchData();
        String version = sha256(source);
        Object cachedVersion = redisTemplate.opsForValue().get(Constants.BANGUMI_DATA_SYNC_VERSION_KEY);

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                Constants.BANGUMI_DATA_SYNC_LOCK_KEY,
                version,
                Duration.ofSeconds(properties.getLockTtlSeconds()));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("bangumi-data sync already running, skip");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(source);
            JsonNode sourceItems = root.path("items");
            JsonNode sourceSites = root.path("siteMeta");
            if (!sourceItems.isArray() || sourceItems.isEmpty()) {
                throw new IllegalStateException("bangumi-data payload has no items; refusing to replace database data");
            }
            if (!sourceSites.isObject() || sourceSites.isEmpty()) {
                throw new IllegalStateException("bangumi-data payload has no siteMeta; refusing to replace database data");
            }

            List<BangumiDataItem> items = parseItems(sourceItems, version);
            List<BangumiDataSiteMeta> sites = parseSites(sourceSites, version);
            if (items.isEmpty() || sites.isEmpty()) {
                throw new IllegalStateException("bangumi-data payload contains no valid records");
            }

            if (version.equals(String.valueOf(cachedVersion))
                    && mapper.countItemsByVersion(version) == items.size()
                    && mapper.countSitesByVersion(version) == sites.size()) {
                log.info("bangumi-data is up to date: {}", version);
                return;
            }

            int batchSize = Math.max(1, properties.getBatchSize());
            for (int start = 0; start < items.size(); start += batchSize) {
                mapper.upsertItems(items.subList(start, Math.min(start + batchSize, items.size())));
            }
            for (int start = 0; start < sites.size(); start += batchSize) {
                mapper.upsertSiteMeta(sites.subList(start, Math.min(start + batchSize, sites.size())));
            }

            // 仅在完整拉取、解析和写入成功后清除旧版本，避免空响应或中途失败导致误删。
            deleteStaleRecords(version, batchSize);
            redisTemplate.opsForValue().set(Constants.BANGUMI_DATA_SYNC_VERSION_KEY, version);
            log.info("bangumi-data sync completed: {} items, {} sites, version={}", items.size(), sites.size(), version);
        } finally {
            redisTemplate.delete(Constants.BANGUMI_DATA_SYNC_LOCK_KEY);
        }
    }

    private String fetchData() throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getDataUrl()))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Accept", "application/json")
                    .header("User-Agent", "AnimeFlow-Service bangumi-data sync")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Failed to fetch bangumi-data, HTTP " + response.statusCode());
            }
            return response.body();
        }
    }

    private List<BangumiDataItem> parseItems(JsonNode sourceItems, String version) throws Exception {
        List<BangumiDataItem> result = new ArrayList<>();
        for (JsonNode node : sourceItems) {
            String title = text(node, "title");
            String begin = text(node, "begin");
            String officialSite = text(node, "officialSite");
            if (title.isBlank() || begin.isBlank()) continue;

            String bangumiId = null;
            JsonNode itemSites = node.path("sites");
            if (itemSites.isArray()) {
                for (JsonNode site : itemSites) {
                    if ("bangumi".equals(text(site, "site"))) {
                        bangumiId = text(site, "id");
                        break;
                    }
                }
            }

            BangumiDataItem item = new BangumiDataItem();
            item.setItemKey((bangumiId == null || bangumiId.isBlank())
                    ? "sha256:" + sha256(title + "\n" + begin + "\n" + officialSite)
                    : "bangumi:" + bangumiId);
            item.setBangumiId(bangumiId == null || bangumiId.isBlank() ? null : bangumiId);
            item.setTitle(title);
            item.setType(text(node, "type"));
            item.setLang(text(node, "lang"));
            item.setOfficialSite(officialSite);
            item.setBegin(begin);
            item.setEnd(text(node, "end"));
            item.setRawData(objectMapper.writeValueAsString(node));
            item.setSyncVersion(version);
            result.add(item);
        }
        return result;
    }

    private List<BangumiDataSiteMeta> parseSites(JsonNode sourceSites, String version) throws Exception {
        List<BangumiDataSiteMeta> result = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : sourceSites.properties()) {
            JsonNode node = entry.getValue();
            BangumiDataSiteMeta site = new BangumiDataSiteMeta();
            site.setSiteKey(entry.getKey());
            site.setTitle(text(node, "title"));
            site.setType(text(node, "type"));
            site.setUrlTemplate(text(node, "urlTemplate"));
            JsonNode regions = node.path("regions");
            site.setRegions(regions.isArray() ? objectMapper.writeValueAsString(regions) : "[]");
            site.setSyncVersion(version);
            if (!site.getTitle().isBlank() && !site.getUrlTemplate().isBlank()) result.add(site);
        }
        return result;
    }

    private void deleteStaleRecords(String version, int batchSize) {
        List<String> itemKeys;
        while (!(itemKeys = mapper.selectStaleItemKeys(version, batchSize)).isEmpty()) {
            mapper.deleteItemsByKeys(itemKeys);
        }

        List<String> siteKeys;
        while (!(siteKeys = mapper.selectStaleSiteKeys(version, batchSize)).isEmpty()) {
            mapper.deleteSitesByKeys(siteKeys);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
