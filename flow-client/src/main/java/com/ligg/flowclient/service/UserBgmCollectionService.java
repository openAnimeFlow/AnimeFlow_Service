package com.ligg.flowclient.service;

import com.ligg.flowclient.module.vo.CollectionUpdateVo;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.flowclient.module.dto.UpdateUserCollectionDto;

public interface UserBgmCollectionService {

    /**
     * 查询当前登录用户的本地收藏列表。
     * accessToken 可能为null
     */
    UserCollectionsVo listMyCollections(
            String accessToken,
            long userId,
            int subjectType,
            int type,
            String keyword,
            int limit,
            int offset);

    /**
     * 保存本地收藏；已绑定时尝试上传，返回独立的远端同步状态。
     */
    CollectionUpdateVo updateCollection(Long userId, int subjectId, UpdateUserCollectionDto dto);
}
