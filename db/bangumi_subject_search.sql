-- Existing database migration for local subject search.
-- The table is filled and refreshed by flow-scheduler when subject archive data is upserted.

CREATE TABLE IF NOT EXISTS `bangumi_subject_search`  (
  `subject_id` int UNSIGNED NOT NULL COMMENT '条目 ID',
  `type` tinyint UNSIGNED NOT NULL COMMENT '作品类型：1漫画 2动画 3音乐 4游戏 6三次元',
  `nsfw` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否 NSFW',
  `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '条目名',
  `name_cn` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '条目简体中文名',
  `aliases` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '从 infobox 提取的别名',
  `tags_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '标签名称文本',
  `meta_tags_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '公共标签文本',
  `year` smallint UNSIGNED NULL DEFAULT NULL COMMENT '发行年份',
  `platform` smallint UNSIGNED NOT NULL DEFAULT 0 COMMENT '条目平台',
  `score` decimal(4, 1) NULL DEFAULT NULL COMMENT '评分',
  `subject_rank` int UNSIGNED NULL DEFAULT NULL COMMENT '类别内排名',
  `favorite_done` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '看过收藏数',
  `search_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '合并搜索文本',
  PRIMARY KEY (`subject_id`) USING BTREE,
  FULLTEXT INDEX `ft_subject_search`(`name`, `name_cn`, `aliases`, `tags_text`, `meta_tags_text`, `search_text`) WITH PARSER `ngram`,
  INDEX `idx_subject_search_filter_rank`(`type` ASC, `nsfw` ASC, `subject_rank` ASC) USING BTREE,
  INDEX `idx_subject_search_filter_score`(`type` ASC, `nsfw` ASC, `score` ASC) USING BTREE,
  INDEX `idx_subject_search_filter_year`(`type` ASC, `nsfw` ASC, `year` ASC) USING BTREE,
  INDEX `idx_subject_search_favorite_done`(`type` ASC, `nsfw` ASC, `favorite_done` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Bangumi 条目搜索索引' ROW_FORMAT = Dynamic;

INSERT INTO bangumi_subject_search (subject_id, type, nsfw, name, name_cn, aliases, tags_text, meta_tags_text, year, platform, score, subject_rank, favorite_done, search_text)
SELECT p.subject_id,
       p.type,
       p.nsfw,
       p.name,
       p.name_cn,
       p.aliases,
       p.tags_text,
       p.meta_tags_text,
       p.year,
       p.platform,
       p.score,
       p.subject_rank,
       p.favorite_done,
       CONCAT_WS(' ', p.name, p.name_cn, p.aliases, p.tags_text, p.meta_tags_text) AS search_text
FROM (
    SELECT s.id AS subject_id,
           s.type,
           s.nsfw,
           s.name,
           s.name_cn,
           '' AS aliases,
           (
               SELECT GROUP_CONCAT(DISTINCT tag_item.name SEPARATOR ' ')
               FROM JSON_TABLE(COALESCE(s.tags, CAST('[]' AS JSON)), '$[*]' COLUMNS (name VARCHAR(255) PATH '$.name')) tag_item
           ) AS tags_text,
           (
               SELECT GROUP_CONCAT(DISTINCT meta_item.name SEPARATOR ' ')
               FROM JSON_TABLE(COALESCE(s.meta_tags, CAST('[]' AS JSON)), '$[*]' COLUMNS (name VARCHAR(255) PATH '$')) meta_item
           ) AS meta_tags_text,
           CASE WHEN s.date REGEXP '^[0-9]{4}' THEN CAST(SUBSTRING(s.date, 1, 4) AS UNSIGNED) ELSE NULL END AS year,
           s.platform,
           s.score,
           s.`rank` AS subject_rank,
           COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.favorite, '$.done')) AS UNSIGNED), 0) AS favorite_done
    FROM bangumi_subject s
) p
ON DUPLICATE KEY UPDATE
    type = VALUES(type),
    nsfw = VALUES(nsfw),
    name = VALUES(name),
    name_cn = VALUES(name_cn),
    aliases = COALESCE(NULLIF(aliases, ''), VALUES(aliases)),
    tags_text = VALUES(tags_text),
    meta_tags_text = VALUES(meta_tags_text),
    year = VALUES(year),
    platform = VALUES(platform),
    score = VALUES(score),
    subject_rank = VALUES(subject_rank),
    favorite_done = VALUES(favorite_done),
    search_text = CONCAT_WS(' ', VALUES(name), VALUES(name_cn), COALESCE(NULLIF(aliases, ''), VALUES(aliases)), VALUES(tags_text), VALUES(meta_tags_text));
