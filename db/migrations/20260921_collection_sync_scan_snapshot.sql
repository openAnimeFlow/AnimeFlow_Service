-- Run after 20260920_collection_sync_recovery.sql, before deploying the snapshot reader.
ALTER TABLE user_bgm_collection_sync_item
    ADD scan_snapshot JSON NULL COMMENT '收藏分页条目及interest快照，首次比较与导入复用；NULL兼容旧任务';
