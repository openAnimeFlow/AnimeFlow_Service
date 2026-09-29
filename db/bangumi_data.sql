-- bangumi-data dataset storage (CC BY 4.0; attribution: bangumi-data)
CREATE TABLE IF NOT EXISTS `bangumi_data_item` (
  `item_key` varchar(96) NOT NULL COMMENT '稳定记录键：Bangumi ID 或内容哈希',
  `bangumi_id` varchar(64) DEFAULT NULL COMMENT 'bangumi-data sites 中的 Bangumi 条目 ID',
  `title` varchar(512) NOT NULL,
  `item_type` varchar(16) NOT NULL,
  `lang` varchar(16) NOT NULL,
  `official_site` varchar(2048) NOT NULL,
  `begin_date` varchar(32) NOT NULL,
  `end_date` varchar(32) DEFAULT NULL,
  `raw_data` json NOT NULL COMMENT '原始番组对象，保留译名、broadcast、comment、sites 等字段',
  `sync_version` char(64) NOT NULL COMMENT '同步源 JSON 的 SHA-256',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`item_key`),
  KEY `idx_bangumi_data_bangumi_id` (`bangumi_id`),
  KEY `idx_bangumi_data_begin_date` (`begin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='bangumi-data 番组数据';

CREATE TABLE IF NOT EXISTS `bangumi_data_site_meta` (
  `site_key` varchar(64) NOT NULL,
  `title` varchar(255) NOT NULL,
  `site_type` varchar(32) NOT NULL,
  `url_template` varchar(2048) NOT NULL,
  `regions` json NOT NULL,
  `sync_version` char(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`site_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='bangumi-data 站点元数据';
