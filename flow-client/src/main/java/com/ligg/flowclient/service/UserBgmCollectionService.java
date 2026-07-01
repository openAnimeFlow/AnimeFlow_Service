package com.ligg.flowclient.service;

import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;

public interface UserBgmCollectionService {

    /**
     * 查询当前登录用户已同步到本地的 Bangumi 收藏列表。
     * accessToken 可能为null
     */
    UserCollectionsVo listMyCollections(String accessToken, long userId, int subjectType, int type, int limit, int offset);

    /**
     * 更新当前用户对条目的 Bangumi 收藏，并同步写入本地 {@code user_bgm_collection}。
     */
    void updateCollection(String accessToken, int subjectId, UpdateUserCollectionDto dto);
}
