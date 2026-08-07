-- 为已部署的 api_access_log 表增加重试幂等键。
-- 按顺序执行一次；既有记录先回填随机事件 ID，再建立唯一索引。
ALTER TABLE `api_access_log`
  ADD COLUMN `event_id` char(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '访问事件唯一 ID，用于重试幂等' AFTER `id`;

UPDATE `api_access_log`
SET `event_id` = REPLACE(UUID(), '-', '')
WHERE `event_id` IS NULL;

ALTER TABLE `api_access_log`
  MODIFY COLUMN `event_id` char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '访问事件唯一 ID，用于重试幂等',
  ADD UNIQUE INDEX `uk_event_id` (`event_id` ASC);
