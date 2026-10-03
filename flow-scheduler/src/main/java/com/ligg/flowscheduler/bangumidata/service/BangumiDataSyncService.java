package com.ligg.flowscheduler.bangumidata.service;

    import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 从 bangumi-data 发布产物同步番组与站点元数据到本地数据库。
 *
 * <p>发布产物体积较大，因此整条链路都按流式处理：响应体边读边写入临时文件并同步计算 SHA-256，
 * 解析时用 Jackson 流式 API 逐条读取数组/对象元素，攒够一个批次就落库并释放，
 * 避免把整份 JSON、语法树和全部实体同时驻留在堆内存中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BangumiDataSyncService {

    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_SITE_META = "siteMeta";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

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

        DownloadedPayload payload = downloadPayload();
        try {
            String version = payload.version();
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
                PayloadCounts counts = countPayload(payload.file());
                if (counts.items() == 0) {
                    throw new IllegalStateException("bangumi-data payload has no items; refusing to replace database data");
                }
                if (counts.sites() == 0) {
                    throw new IllegalStateException("bangumi-data payload has no siteMeta; refusing to replace database data");
                }

                if (version.equals(String.valueOf(cachedVersion))
                        && mapper.countItemsByVersion(version) == counts.items()
                        && mapper.countSitesByVersion(version) == counts.sites()) {
                    log.info("bangumi-data is up to date: {}", version);
                    return;
                }

                int batchSize = Math.max(1, properties.getBatchSize());
                importItems(payload.file(), version, batchSize);
                importSites(payload.file(), version, batchSize);

                // 仅在完整拉取、解析和写入成功后清除旧版本，避免空响应或中途失败导致误删。
                deleteStaleRecords(version, batchSize);
                redisTemplate.opsForValue().set(Constants.BANGUMI_DATA_SYNC_VERSION_KEY, version);
                log.info("bangumi-data sync completed: {} items, {} sites, version={}",
                        counts.items(), counts.sites(), version);
            } finally {
                redisTemplate.delete(Constants.BANGUMI_DATA_SYNC_LOCK_KEY);
            }
        } finally {
            deletePayloadQuietly(payload.file());
        }
    }

    /** 流式下载发布产物：写入临时文件的同时计算 SHA-256，避免整包字符串常驻内存。 */
    private DownloadedPayload downloadPayload() throws Exception {
        Path target = Files.createTempFile("bangumi-data-", ".json");
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

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalBytes = 0L;
            try (OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                HttpResponse<InputStream> response = client.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IllegalStateException(
                            "Failed to fetch bangumi-data, HTTP " + response.statusCode());
                }
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                try (InputStream in = response.body()) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                        totalBytes += read;
                    }
                }
            }
            if (totalBytes == 0L) {
                throw new IllegalStateException("bangumi-data response body is empty");
            }
            log.info("bangumi-data payload downloaded: {} bytes", totalBytes);
            return new DownloadedPayload(target, HexFormat.of().formatHex(digest.digest()));
        } catch (Exception e) {
            deletePayloadQuietly(target);
            throw e;
        }
    }

    /** 只统计有效记录数，用于判断数据库中的当前版本是否已是最新。逐条读取、不保留实体。 */
    private PayloadCounts countPayload(Path file) throws Exception {
        int[] items = {0};
        int[] sites = {0};
        forEachRootField(file, (field, parser) -> {
            if (FIELD_ITEMS.equals(field)) {
                if (parser.currentToken() != JsonToken.START_ARRAY) return;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (isValidItem(objectMapper.readTree(parser))) items[0]++;
                }
            } else {
                if (parser.currentToken() != JsonToken.START_OBJECT) return;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    parser.nextToken();
                    if (isValidSite(objectMapper.readTree(parser))) sites[0]++;
                }
            }
        });
        return new PayloadCounts(items[0], sites[0]);
    }

    /** 流式读取 items 数组，按批 upsert，任意时刻堆内只保留一个批次的实体。 */
    private void importItems(Path file, String version, int batchSize) throws Exception {
        List<BangumiDataItem> batch = new ArrayList<>(batchSize);
        forEachRootField(file, (field, parser) -> {
            if (!FIELD_ITEMS.equals(field)) return;
            if (parser.currentToken() != JsonToken.START_ARRAY) return;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                BangumiDataItem item = toItem(objectMapper.readTree(parser), version);
                if (item == null) continue;
                batch.add(item);
                if (batch.size() >= batchSize) {
                    mapper.upsertItems(batch);
                    batch.clear();
                }
            }
        });
        if (!batch.isEmpty()) {
            mapper.upsertItems(batch);
            batch.clear();
        }
    }

    /** 流式读取 siteMeta 对象，按批 upsert。 */
    private void importSites(Path file, String version, int batchSize) throws Exception {
        List<BangumiDataSiteMeta> batch = new ArrayList<>(batchSize);
        forEachRootField(file, (field, parser) -> {
            if (!FIELD_SITE_META.equals(field)) return;
            if (parser.currentToken() != JsonToken.START_OBJECT) return;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String siteKey = parser.currentName();
                parser.nextToken();
                JsonNode node = objectMapper.readTree(parser);
                if (!isValidSite(node)) continue;
                batch.add(toSite(siteKey, node, version));
                if (batch.size() >= batchSize) {
                    mapper.upsertSiteMeta(batch);
                    batch.clear();
                }
            }
        });
        if (!batch.isEmpty()) {
            mapper.upsertSiteMeta(batch);
            batch.clear();
        }
    }

    /** 只遍历根对象的字段，命中回调后跳过其余容器字段，避免深入无关结构。 */
    private void forEachRootField(Path file, RootFieldVisitor visitor) throws Exception {
        try (JsonParser parser = objectMapper.getFactory().createParser(file.toFile())) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    continue;
                }
                String field = parser.currentName();
                parser.nextToken();
                if (FIELD_ITEMS.equals(field) || FIELD_SITE_META.equals(field)) {
                    visitor.visit(field, parser);
                }
                if (parser.currentToken() == JsonToken.START_OBJECT
                        || parser.currentToken() == JsonToken.START_ARRAY) {
                    parser.skipChildren();
                }
            }
        }
    }

    private BangumiDataItem toItem(JsonNode node, String version) throws Exception {
        if (!isValidItem(node)) return null;

        String title = text(node, "title");
        String begin = text(node, "begin");
        String officialSite = text(node, "officialSite");

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
        return item;
    }

    private BangumiDataSiteMeta toSite(String siteKey, JsonNode node, String version) throws Exception {
        BangumiDataSiteMeta site = new BangumiDataSiteMeta();
        site.setSiteKey(siteKey);
        site.setTitle(text(node, "title"));
        site.setType(text(node, "type"));
        site.setUrlTemplate(text(node, "urlTemplate"));
        JsonNode regions = node.path("regions");
        site.setRegions(regions.isArray() ? objectMapper.writeValueAsString(regions) : "[]");
        site.setSyncVersion(version);
        return site;
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

    private static boolean isValidItem(JsonNode node) {
        return node.isObject()
                && !text(node, "title").isBlank()
                && !text(node, "begin").isBlank();
    }

    private static boolean isValidSite(JsonNode node) {
        return node.isObject()
                && !text(node, "title").isBlank()
                && !text(node, "urlTemplate").isBlank();
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static void deletePayloadQuietly(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("failed to delete bangumi-data temp file: {}", file, e);
        }
    }

    private record DownloadedPayload(Path file, String version) {
    }

    private record PayloadCounts(int items, int sites) {
    }

    @FunctionalInterface
    private interface RootFieldVisitor {
        void visit(String field, JsonParser parser) throws Exception;
    }
}
