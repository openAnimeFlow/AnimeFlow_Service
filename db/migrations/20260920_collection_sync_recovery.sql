-- Run after 20260919_collection_sync_tasks.sql. No collection content is changed.
-- Refuse migration if duplicate active tasks already exist; reconcile those tasks first.
ALTER TABLE user_bgm_collection_sync_task
    ADD active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('QUEUED','RUNNING','WAITING_CONFLICT','PARTIAL_FAILED')
          THEN user_id ELSE NULL END) STORED COMMENT '活动任务用户唯一约束，终态为 NULL',
    ADD UNIQUE KEY uk_collection_sync_active_user (active_user_id);

ALTER TABLE user_bgm_collection_sync_item
    ADD desired_payload JSON NULL COMMENT '远端写入前持久化的目标字段，重启后校验基线再执行',
    ADD operation VARCHAR(16) NULL COMMENT 'IMPORT/UPLOAD/UNCHANGED/RESOLVE，统计已完成明细',
    MODIFY status VARCHAR(32) NOT NULL COMMENT 'PLANNED/CONFLICT/RESOLUTION_PENDING/APPLY_PENDING/DONE/FAILED';
