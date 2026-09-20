package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ligg.common.statuenum.CollectionRemoteSyncStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("user_bgm_collection")
public class UserBgmCollectionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer subjectId;

    /**
     * 条目大类：1漫画 2动画 3音乐 4游戏 6三次元（同步时冗余，避免列表查询 JOIN 过滤）。
     */
    private Integer subjectType;

    private Long bgmInterestId;

    private Integer rate;

    private Integer type;

    private String comment;

    private String tags;

    private Integer epStatus;

    private Integer volStatus;

    private Boolean isPrivate;

    private Long bgmUpdatedAt;

    private LocalDateTime syncTime;

    private LocalDateTime createTime;

    /** 本地收藏内容最后修改时间。 */
    private LocalDateTime localUpdatedAt;
    /** 本地收藏乐观锁版本。 */
    private Long version;
    /** LOCAL_ONLY/PENDING/SYNCED/AUTH_REQUIRED/CONFLICT。 */
    private CollectionRemoteSyncStatus remoteSyncStatus;
    /** Durable, coalesced upload intent; never contains OAuth credentials. */
    private String pendingPayload;
    private String remoteBaseline;
    private Long syncOauthId;
    /** 目标 Bangumi 用户 UID。 */
    private Long bgmAccountUid;
    /** 远端同步连续重试次数。 */
    private Integer retryCount;
    /** 下一次远端同步重试时间。 */
    private LocalDateTime nextRetryAt;
}
