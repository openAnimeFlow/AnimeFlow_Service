package com.ligg.flowclient.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.api.bangumiv0api.BangumiV0Client;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 条目图片处理服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageBackfillService {

    private final BangumiSubjectMapper bangumiSubjectMapper;
    private final BangumiV0Client bangumiV0Client;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Integer, Object> locks = new ConcurrentHashMap<>();

    /**
     * 获取条目封面图。DB 有数据则直接返回，否则调 API 获取并回写。
     *
     * @param subjectImagesJson bangumi_subject.images 的 JSON 值，可为空
     * @param subjectId         条目 ID
     * @return 封面图，获取失败返回空 CoverImages
     *
     * TODO 需啊添加可选access_token，否有nsfw类型条目获取图片会失败
     */
    public CoverImages resolve(String subjectImagesJson, int subjectId) {
        CoverImages images;
        if (StringUtils.hasText(subjectImagesJson) && !"null".equals(subjectImagesJson)) {
            try {
                images = objectMapper.readValue(subjectImagesJson, CoverImages.class);
            } catch (JsonProcessingException e) {
                log.warn("解析条目图片 JSON 失败, subjectId={}", subjectId, e);
                images = new CoverImages();
            }
        } else {
            images = fetchAndSave(subjectId);
        }
        if (images.getLarge() == null) images.setLarge("");
        if (images.getCommon() == null) images.setCommon("");
        if (images.getMedium() == null) images.setMedium("");
        if (images.getSmall() == null) images.setSmall("");
        if (images.getGrid() == null) images.setGrid("");
        return images;
    }

    private CoverImages fetchAndSave(int subjectId) {
        // 锁对象不删除：避免 remove 后其他线程创建新锁绕过互斥，造成重复请求。
        // subjectId 数量有限（≈3 万），全量常驻仅约 2 MB，可接受。
        Object lock = locks.computeIfAbsent(subjectId, k -> new Object());
        synchronized (lock) {
            // 双重检查
            BangumiSubjectEntity entity = bangumiSubjectMapper.selectById(subjectId);
            if (entity != null && StringUtils.hasText(entity.getImages())
                    && !"null".equals(entity.getImages())) {
                try {
                    return objectMapper.readValue(entity.getImages(), CoverImages.class);
                } catch (JsonProcessingException e) {
                    log.warn("双重检查时解析条目图片 JSON 失败, subjectId={}", subjectId, e);
                    // JSON 损坏，fall-through 重新从 API 获取
                }
            }

            CoverImages images = new CoverImages();
            boolean anySucceeded = false;

            for (SubjectImageType type : SubjectImageType.values()) {
                try {
                    String url = bangumiV0Client.getSubjectImageUrl(subjectId, type);
                    if (StringUtils.hasText(url)) {
                        setField(images, type, url);
                        anySucceeded = true;
                    }
                } catch (Exception e) {
                    log.warn("获取条目图片失败, subjectId={}, type={}", subjectId, type.getValue(), e);
                }
            }

            if (anySucceeded) {
                try {
                    String json = objectMapper.writeValueAsString(images);
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

    private static void setField(CoverImages images, SubjectImageType type, String url) {
        switch (type) {
            case LARGE -> images.setLarge(url);
            case COMMON -> images.setCommon(url);
            case MEDIUM -> images.setMedium(url);
            case SMALL -> images.setSmall(url);
            case GRID -> images.setGrid(url);
            default -> { /* 枚举新增值时编译器会警告，此处防御性空操作 */ }
        }
    }
}
