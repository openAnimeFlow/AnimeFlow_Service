package com.ligg.flowclient.service;

import com.ligg.common.vo.bangumi.UserCollectionsVo;

public interface UserBgmCollectionService {

    /**
     * 查询当前登录用户已同步到本地的 Bangumi 收藏列表。
     */
    UserCollectionsVo listMyCollections(String accessToken, int subjectType, int type, int limit, int offset);
}
