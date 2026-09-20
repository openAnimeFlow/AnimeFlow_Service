-- Phase 1. Run once, before deploying the new server/client; do not run anime_flow.sql on a live database.
-- Keep legacy `private` values for audit; all new reads/writes use is_private.
ALTER TABLE user_bgm_collection
    MODIFY bgm_interest_id BIGINT NULL COMMENT 'Bangumi 收藏 ID；仅本地收藏为 NULL',
    MODIFY bgm_updated_at BIGINT NULL COMMENT 'Bangumi 更新时间 Unix 秒',
    MODIFY sync_time DATETIME NULL DEFAULT NULL COMMENT '最后成功同步时间',
    ADD local_updated_at DATETIME(3) NULL COMMENT '本地收藏内容最后修改时间（毫秒精度）',
    ADD version BIGINT NOT NULL DEFAULT 0 COMMENT '本地收藏乐观锁版本，每次本地修改递增',
    ADD remote_sync_status ENUM('LOCAL_ONLY','PENDING','SYNCED','AUTH_REQUIRED','CONFLICT') NOT NULL DEFAULT 'LOCAL_ONLY' COMMENT '远端同步状态',
    ADD pending_payload JSON NULL COMMENT '合并后的待上传字段，不包含凭据',
    ADD remote_baseline JSON NULL COMMENT '首次上传前的远端快照',
    ADD sync_oauth_id BIGINT NULL COMMENT '目标绑定记录 ID，解绑后失效',
    ADD bgm_account_uid BIGINT NULL COMMENT '目标 Bangumi 用户 UID，换绑后必须重新建立关联',
    ADD retry_count INT NOT NULL DEFAULT 0 COMMENT '远端同步连续重试次数',
    ADD next_retry_at DATETIME NULL COMMENT '下一次远端同步重试时间',
    DROP INDEX uk_bgm_interest_id,
    ADD INDEX idx_bgm_interest_id (bgm_interest_id),
    ADD INDEX idx_user_collection_local (user_id, type, subject_type, local_updated_at DESC, id DESC),
    ADD INDEX idx_collection_upload (remote_sync_status, next_retry_at);

UPDATE user_bgm_collection
SET local_updated_at = COALESCE(sync_time, create_time, CURRENT_TIMESTAMP);

-- Historical rows remain version=0, without upload intent. A later edit/import establishes synchronization state.
