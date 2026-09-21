-- Speed up recovery polling by filtering active statuses and ordering by recovery time.
ALTER TABLE user_bgm_collection_sync_task
    ADD INDEX idx_sync_task_recovery (status, heartbeat_at, created_at, id);
