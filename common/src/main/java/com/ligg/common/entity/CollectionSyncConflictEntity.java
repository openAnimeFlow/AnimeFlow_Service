package com.ligg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ligg.common.statuenum.CollectionSyncItemStatus;
import com.ligg.common.statuenum.CollectionSyncOperation;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_bgm_collection_sync_item")
public class CollectionSyncConflictEntity {
    /** 冲突明细主键，同时作为客户端展示的冲突 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属同步任务。 */
    private Long taskId;
    /** AnimeFlow 用户 ID。 */
    private Long userId;
    /** Bangumi 条目 ID。 */
    private Integer subjectId;
    /** 条目大类。 */
    private Integer subjectType;
    /** 冲突发生时的条目名称。 */
    private String subjectName;
    /** 冲突发生时的条目封面。 */
    private String subjectImage;
    /** 冲突发生时的本地分类。 */
    private Integer localType;
    /** 冲突发生时的 Bangumi 分类。 */
    private Integer remoteType;
    /** 冲突发生时的本地版本。 */
    private Long localVersion;
    /** 客户端提交决议时必须匹配的冲突版本。 */
    private Long conflictVersion;
    /** 用户选择的最终分类。 */
    private Integer selectedType;
    /** 本地收藏字段快照。 */
    private String localSnapshot;
    /** Bangumi 收藏字段快照。 */
    private String remoteSnapshot;
    /** 收藏分页返回的条目与 interest 快照，仅用于首次比较和导入。 */
    private String scanSnapshot;
    /** 已提交的远端写入意图，网络请求前持久化。 */
    private String desiredPayload;
    /** IMPORT/UPLOAD/UNCHANGED/RESOLVE，成功后用于统计。 */
    private CollectionSyncOperation operation;
    /** PLANNED/CONFLICT/RESOLUTION_PENDING/APPLY_PENDING/DONE/FAILED。 */
    private CollectionSyncItemStatus status;
    /** 明细错误码。 */
    private String errorCode;
    /** 冲突创建时间。 */
    private LocalDateTime createdAt;
    /** 远端确认完成时间。 */
    private LocalDateTime resolvedAt;
}
