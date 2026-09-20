package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ligg.common.statuenum.BgmCollectionSyncStatus;
import com.ligg.common.statuenum.CollectionSyncPhase;
import com.ligg.common.statuenum.CollectionSyncTaskErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_bgm_collection_sync_task")
public class CollectionSyncTaskEntity {
    /** 同步任务主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** AnimeFlow 用户 ID。 */
    private Long userId;
    /** 本次任务的条目大类。 */
    private Integer subjectType;
    /** 目标 Bangumi 用户 UID。 */
    private Long bgmAccountUid;
    /** 目标 OAuth 绑定记录 ID。 */
    private Long oauthId;
    /** 客户端幂等请求 ID。 */
    private String requestId;
    /** QUEUED/RUNNING/WAITING_CONFLICT/PARTIAL_FAILED/SUCCESS/FAILED/CANCELLED。 */
    private BgmCollectionSyncStatus status;
    /** SCANNING/APPLYING/RESOLVING。 */
    private CollectionSyncPhase phase;
    /** 去重后的任务总条目数。 */
    private Integer totalCount;
    /** 从 Bangumi 导入的条目数。 */
    private Integer importedCount;
    /** 上传到 Bangumi 的条目数。 */
    private Integer uploadedCount;
    /** 双方一致、无需处理的条目数。 */
    private Integer unchangedCount;
    /** 当前待处理冲突数。 */
    private Integer conflictCount;
    /** 已完成决议并确认同步的冲突数。 */
    private Integer resolvedCount;
    /** 可重试失败数。 */
    private Integer failedCount;
    /** 任务状态版本。 */
    private Long statusVersion;
    /** 任务级错误码。 */
    private CollectionSyncTaskErrorCode errorCode;
    /** 任务创建时间。 */
    private LocalDateTime createdAt;
    /** 首次执行时间。 */
    private LocalDateTime startedAt;
    /** 进入终态时间。 */
    private LocalDateTime finishedAt;
    /** 执行器最近心跳时间。 */
    private LocalDateTime heartbeatAt;
}
