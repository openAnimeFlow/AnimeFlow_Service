package com.ligg.flowclient.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ligg.api.bangumiv0api.BangumiV0Client;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;
import com.ligg.common.utils.BangumiSeasonUtils;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 条目图片处理服务
 */
@Slf4j
@Component
public class ImageBackfillService {

    private static final String FETCHED_AT = "fetched_at";
    private static final Duration REFRESH_INTERVAL = Duration.ofDays(7);
    private final BangumiSubjectMapper bangumiSubjectMapper;
    private final BangumiV0Client bangumiV0Client;
    private final ObjectMapper objectMapper;
    private final Executor refreshExecutor;

    private final ConcurrentHashMap<Integer, Object> locks = new ConcurrentHashMap<>();
    private final Set<Integer> refreshing = ConcurrentHashMap.newKeySet();

    public ImageBackfillService(BangumiSubjectMapper bangumiSubjectMapper,
                                BangumiV0Client bangumiV0Client,
                                ObjectMapper objectMapper,
                                @Qualifier("subjectImageRefreshExecutor") Executor refreshExecutor) {
        this.bangumiSubjectMapper = bangumiSubjectMapper;
        this.bangumiV0Client = bangumiV0Client;
        this.objectMapper = objectMapper;
        this.refreshExecutor = refreshExecutor;
    }

    /**
     * 详情页优先返回已有封面，本季度动画缺少有效获取时间或最近成功获取已满一周时异步刷新。
     * 历史封面在首次成功同步后补全获取时间，后续按一周间隔刷新。
     * fetched_at 以 UTC ISO-8601 字符串保存在 images JSON 中，不返回给封面 DTO。
     */
    public CoverImages resolve(BangumiSubjectEntity subject, String accessToken) {
        CoverImages cached = readImages(subject.getImages(), subject.getId());
        if (cached == null) {
            return normalize(fetchAndSave(subject.getId(), accessToken, false));
        }
        if (needsRefresh(subject)) {
            refreshAsync(subject.getId(), accessToken);
        }
        return normalize(cached);
    }

    private boolean needsRefresh(BangumiSubjectEntity subject) {
        if (!Integer.valueOf(2).equals(subject.getType()) || !StringUtils.hasText(subject.getDate())) {
            return false;
        }
        try {
            LocalDate releaseDate = LocalDate.parse(subject.getDate());
            LocalDate today = LocalDate.now();
            if (releaseDate.getYear() != today.getYear()
                    || BangumiSeasonUtils.startMonthOf(releaseDate.getMonthValue())
                    != BangumiSeasonUtils.startMonthOf(today.getMonthValue())) {
                return false;
            }
            Instant fetchedAt = readFetchedAt(subject.getImages());
            return fetchedAt == null || !fetchedAt.isAfter(Instant.now().minus(REFRESH_INTERVAL));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private Instant readFetchedAt(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            JsonNode node = objectMapper.readTree(json);
            String value = node == null ? "" : node.path(FETCHED_AT).asText("");
            return StringUtils.hasText(value) ? Instant.parse(value) : null;
        } catch (JsonProcessingException | DateTimeParseException e) {
            return null;
        }
    }

    private void refreshAsync(int subjectId, String accessToken) {
        if (!refreshing.add(subjectId)) return;
        try {
            refreshExecutor.execute(() -> {
                try {
                    fetchAndSave(subjectId, accessToken, true);
                } catch (RuntimeException e) {
                    log.warn("异步刷新条目封面失败, subjectId={}", subjectId, e);
                } finally {
                    refreshing.remove(subjectId);
                }
            });
        } catch (RejectedExecutionException e) {
            refreshing.remove(subjectId);
            log.debug("条目封面刷新队列已满，留待下次请求重试, subjectId={}", subjectId);
        }
    }

    /**
     * 获取条目封面图。DB 有数据则直接返回，否则调 API 获取并回写。
     *
     * @param subjectImagesJson bangumi_subject.images 的 JSON 值，可为空
     * @param subjectId         条目 ID
     * @param accessToken       Bangumi OAuth access token，可为 null（传 null 时不会在请求中携带）
     * @return 封面图，获取失败返回空 CoverImages
     */
    public CoverImages resolve(String subjectImagesJson, int subjectId, String accessToken) {
        CoverImages images = readImages(subjectImagesJson, subjectId);
        return normalize(images != null ? images : fetchAndSave(subjectId, accessToken, false));
    }

    private CoverImages readImages(String json, int subjectId) {
        if (!StringUtils.hasText(json)) return null;
        try {
            CoverImages images = objectMapper.readValue(json, CoverImages.class);
            return images != null && (StringUtils.hasText(images.getLarge())
                    || StringUtils.hasText(images.getCommon()) || StringUtils.hasText(images.getMedium())
                    || StringUtils.hasText(images.getSmall()) || StringUtils.hasText(images.getGrid()))
                    ? images : null;
        } catch (JsonProcessingException e) {
            log.warn("解析条目图片 JSON 失败, subjectId={}", subjectId, e);
            return null;
        }
    }

    private CoverImages normalize(CoverImages images) {
        if (images.getLarge() == null) images.setLarge("");
        if (images.getCommon() == null) images.setCommon("");
        if (images.getMedium() == null) images.setMedium("");
        if (images.getSmall() == null) images.setSmall("");
        if (images.getGrid() == null) images.setGrid("");
        return images;
    }

    private CoverImages fetchAndSave(int subjectId, String accessToken, boolean refresh) {
        // 锁对象不删除：避免 remove 后其他线程创建新锁绕过互斥，造成重复请求。
        // subjectId 数量有限（≈3 万），全量常驻仅约 2 MB，可接受。
        Object lock = locks.computeIfAbsent(subjectId, k -> new Object());
        synchronized (lock) {
            // 双重检查
            BangumiSubjectEntity entity = bangumiSubjectMapper.selectById(subjectId);
            if (entity == null) return new CoverImages();
            CoverImages cached = readImages(entity.getImages(), subjectId);
            if (cached != null && (!refresh || !needsRefresh(entity))) {
                return cached;
            }

            // 某个尺寸获取失败时保留原地址，全部失败则不更新 JSON 和成功时间。
            CoverImages images = cached != null ? cached : new CoverImages();
            boolean anySucceeded = fetchImages(subjectId, accessToken, images);

            if (anySucceeded) {
                try {
                    ObjectNode jsonNode = objectMapper.valueToTree(images);
                    jsonNode.put(FETCHED_AT, Instant.now().toString());
                    String json = objectMapper.writeValueAsString(jsonNode);
                    BangumiSubjectEntity update = new BangumiSubjectEntity();
                    update.setId(subjectId);
                    update.setImages(json);
                    bangumiSubjectMapper.updateById(update);
                    log.info("条目图片已回写 DB, subjectId={}, images={}", subjectId, json);
                } catch (JsonProcessingException e) {
                    log.error("序列化条目图片 JSON 失败, subjectId={}", subjectId, e);
                }
            } else {
                log.warn("条目图片获取全部失败, subjectId={}", subjectId);
            }
            return images;
        }
    }

    private boolean fetchImages(int subjectId, String accessToken, CoverImages images) {
        boolean anySucceeded = false;
        for (SubjectImageType type : SubjectImageType.values()) {
            try {
                String url = bangumiV0Client.getSubjectImageUrl(subjectId, type, accessToken);
                if (StringUtils.hasText(url)) {
                    setField(images, type, url);
                    anySucceeded = true;
                }
            } catch (Exception e) {
                log.warn("获取条目图片失败, subjectId={}, type={}", subjectId, type.getValue(), e);
            }
        }
        return anySucceeded;
    }

    private static void setField(CoverImages images, SubjectImageType type, String url) {
        ImageSetter setter = switch (type) {
            case LARGE -> CoverImages::setLarge;
            case COMMON -> CoverImages::setCommon;
            case MEDIUM -> CoverImages::setMedium;
            case SMALL -> CoverImages::setSmall;
            case GRID -> CoverImages::setGrid;
        };
        setter.set(images, url);
    }

    private interface ImageSetter {
        void set(CoverImages images, String url);
    }
}
