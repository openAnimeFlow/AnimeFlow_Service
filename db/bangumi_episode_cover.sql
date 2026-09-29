-- Existing database migration: persist TMDb episode stills or movie posters.
ALTER TABLE `bangumi_episode`
  ADD COLUMN `cover` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '剧集封面 URL';
