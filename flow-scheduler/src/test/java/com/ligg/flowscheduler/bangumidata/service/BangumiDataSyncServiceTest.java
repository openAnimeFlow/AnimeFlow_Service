package com.ligg.flowscheduler.bangumidata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.flowscheduler.bangumidata.config.BangumiDataSyncProperties;
import com.ligg.flowscheduler.bangumidata.mapper.BangumiDataMapper;
import com.ligg.flowscheduler.bangumidata.model.BangumiDataItem;
import com.ligg.flowscheduler.bangumidata.model.BangumiDataSiteMeta;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BangumiDataSyncServiceTest {

    private final BangumiDataMapper mapper = mock(BangumiDataMapper.class);
    private final BangumiDataSyncProperties properties = new BangumiDataSyncProperties();
    private final Map<String, Object> redisStore = new HashMap<>();
    private final AtomicInteger downloads = new AtomicInteger();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisTemplate<String, Object> redisTemplate;
    private BangumiDataSyncService service;
    private HttpServer server;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisStore.get(invocation.getArgument(0, String.class)));
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0, String.class);
                    if (redisStore.containsKey(key)) return false;
                    redisStore.put(key, invocation.getArgument(1));
                    return true;
                });
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any());
        when(redisTemplate.delete(anyString()))
                .thenAnswer(invocation -> redisStore.remove(invocation.getArgument(0, String.class)) != null);

        service = new BangumiDataSyncService(properties, mapper, redisTemplate, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void streamsPayloadAndUpsertsInBatches() throws Exception {
        String payload = payload(String.join(",",
                        itemJson(0, "1001"), itemJson(1, null), itemJson(2, null),
                        "{\"title\":\"\",\"begin\":\"2024-01-01\"}", itemJson(3, null), itemJson(4, null)),
                String.join(",", siteJson("bilibili"), siteJson("bangumi"), siteJson("acfun")));
        long tempFilesBefore = tempPayloadCount();
        properties.setDataUrl(serve(payload));
        properties.setBatchSize(2);

        List<List<BangumiDataItem>> itemBatches = recordItemBatches();
        List<List<BangumiDataSiteMeta>> siteBatches = recordSiteBatches();

        service.syncIfNeeded();

        verify(mapper, times(3)).upsertItems(any());
        assertThat(itemBatches).extracting(List::size).containsExactly(2, 2, 1);
        List<BangumiDataItem> items = itemBatches.stream().flatMap(List::stream).toList();
        assertThat(items).hasSize(5);
        assertThat(items.get(0).getItemKey()).isEqualTo("bangumi:1001");
        assertThat(items.subList(1, 5)).extracting(BangumiDataItem::getItemKey)
                .allSatisfy(key -> assertThat(key).startsWith("sha256:"));
        assertThat(items.subList(1, 5)).extracting(BangumiDataItem::getItemKey).doesNotHaveDuplicates();
        assertThat(items.get(0).getBangumiId()).isEqualTo("1001");
        assertThat(items).allSatisfy(item -> {
            assertThat(item.getRawData()).isNotBlank();
            assertThat(item.getSyncVersion()).isEqualTo(sha256(payload));
        });
        assertThat(items.get(0).getRawData()).contains("\"title\":\"番组0\"");

        verify(mapper, times(2)).upsertSiteMeta(any());
        assertThat(siteBatches).extracting(List::size).containsExactly(2, 1);
        assertThat(siteBatches.get(0).get(0).getRegions()).isEqualTo("[\"CN\"]");

        assertThat(downloads).hasValue(1);
        assertThat(redisStore)
                .containsEntry(Constants.BANGUMI_DATA_SYNC_VERSION_KEY, sha256(payload))
                .doesNotContainKey(Constants.BANGUMI_DATA_SYNC_LOCK_KEY);
        assertThat(tempPayloadCount()).isEqualTo(tempFilesBefore);
    }

    @Test
    void skipsImportWhenVersionAndCountsAlreadyMatch() throws Exception {
        String payload = payload(String.join(",", itemJson(0, "1001"), itemJson(1, null)),
                siteJson("bilibili"));
        properties.setDataUrl(serve(payload));
        properties.setBatchSize(2);
        service.syncIfNeeded();

        reset(mapper);
        when(mapper.countItemsByVersion(sha256(payload))).thenReturn(2);
        when(mapper.countSitesByVersion(sha256(payload))).thenReturn(1);

        service.syncIfNeeded();

        verify(mapper, never()).upsertItems(any());
        verify(mapper, never()).upsertSiteMeta(any());
        verify(mapper, never()).selectStaleItemKeys(anyString(), anyInt());
        verify(mapper).countItemsByVersion(sha256(payload));
        verify(mapper).countSitesByVersion(sha256(payload));
        assertThat(downloads).hasValue(2);
        assertThat(redisStore).doesNotContainKey(Constants.BANGUMI_DATA_SYNC_LOCK_KEY);
    }

    @Test
    void reimportsWhenDatabaseIsMissingRowsForCachedVersion() throws Exception {
        String payload = payload(String.join(",", itemJson(0, "1001"), itemJson(1, null)),
                siteJson("bilibili"));
        properties.setDataUrl(serve(payload));
        properties.setBatchSize(2);
        service.syncIfNeeded();

        reset(mapper);
        when(mapper.countItemsByVersion(sha256(payload))).thenReturn(1);
        when(mapper.countSitesByVersion(sha256(payload))).thenReturn(1);

        service.syncIfNeeded();

        verify(mapper).upsertItems(any());
        verify(mapper).upsertSiteMeta(any());
    }

    @Test
    void refusesToReplaceDataWhenItemsAreMissing() throws Exception {
        properties.setDataUrl(serve(payload("", siteJson("bilibili"))));
        long tempFilesBefore = tempPayloadCount();

        assertThatThrownBy(service::syncIfNeeded)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no items");

        verify(mapper, never()).upsertItems(any());
        verify(mapper, never()).upsertSiteMeta(any());
        assertThat(redisStore).doesNotContainKey(Constants.BANGUMI_DATA_SYNC_VERSION_KEY);
        assertThat(redisStore).doesNotContainKey(Constants.BANGUMI_DATA_SYNC_LOCK_KEY);
        assertThat(tempPayloadCount()).isEqualTo(tempFilesBefore);
    }

    @Test
    void refusesToReplaceDataWhenSiteMetaIsMissing() throws Exception {
        properties.setDataUrl(serve(payload(itemJson(0, "1001"), "")));

        assertThatThrownBy(service::syncIfNeeded)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no siteMeta");

        verify(mapper, never()).upsertItems(any());
    }

    @Test
    void skipsWorkWhenAnotherInstanceHoldsTheLock() throws Exception {
        properties.setDataUrl(serve(payload(itemJson(0, "1001"), siteJson("bilibili"))));
        redisStore.put(Constants.BANGUMI_DATA_SYNC_LOCK_KEY, "other-instance");

        service.syncIfNeeded();

        verify(mapper, never()).upsertItems(any());
        assertThat(redisStore).containsKey(Constants.BANGUMI_DATA_SYNC_LOCK_KEY);
    }

    private String serve(String payload) throws IOException {
        downloads.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data.json", exchange -> {
            downloads.incrementAndGet();
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/data.json";
    }

    /**
     * 服务端会把同一个批次列表复用后清空，所以必须在调用时复制快照，否则只能观察到最终的空列表。
     */
    @SuppressWarnings("unchecked")
    private List<List<BangumiDataItem>> recordItemBatches() {
        List<List<BangumiDataItem>> batches = new ArrayList<>();
        doAnswer(invocation -> {
            batches.add(List.copyOf((List<BangumiDataItem>) invocation.getArgument(0)));
            return null;
        }).when(mapper).upsertItems(any());
        return batches;
    }

    @SuppressWarnings("unchecked")
    private List<List<BangumiDataSiteMeta>> recordSiteBatches() {
        List<List<BangumiDataSiteMeta>> batches = new ArrayList<>();
        doAnswer(invocation -> {
            batches.add(List.copyOf((List<BangumiDataSiteMeta>) invocation.getArgument(0)));
            return null;
        }).when(mapper).upsertSiteMeta(any());
        return batches;
    }

    private static String payload(String itemsJson, String siteMetaJson) {
        return "{\"meta\":{\"version\":\"test\"},\"items\":[" + itemsJson + "],\"siteMeta\":{"
                + siteMetaJson + "}}";
    }

    private static String itemJson(int index, String bangumiId) {
        String sites = bangumiId == null ? "[]" : "[{\"site\":\"bangumi\",\"id\":\"" + bangumiId + "\"}]";
        return "{\"title\":\"番组" + index + "\",\"type\":\"tv\",\"lang\":\"zh-Hans\","
                + "\"begin\":\"2024-01-01\",\"end\":\"\",\"officialSite\":\"https://example.com/" + index + "\","
                + "\"sites\":" + sites + "}";
    }

    private static String siteJson(String key) {
        return "\"" + key + "\":{\"title\":\"" + key + " 站点\",\"type\":\"onair\","
                + "\"urlTemplate\":\"https://" + key + ".example.com/{id}\",\"regions\":[\"CN\"]}";
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static long tempPayloadCount() throws IOException {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("bangumi-data-") && name.endsWith(".json");
            }).count();
        }
    }
}
