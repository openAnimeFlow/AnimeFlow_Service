/*
 Navicat Premium Dump SQL

 Source Server         : mysql
 Source Server Type    : MySQL
 Source Server Version : 80043 (8.0.43)
 Source Host           : localhost:3306
 Source Schema         : anime_flow

 Target Server Type    : MySQL
 Target Server Version : 80043 (8.0.43)
 File Encoding         : 65001

 Date: 08/06/2026 08:38:18
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for danmaku
-- ----------------------------
DROP TABLE IF EXISTS `danmaku`;
CREATE TABLE `danmaku`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `episode_id` bigint NOT NULL COMMENT '主键ID',
  `comment` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '弹幕内容',
  `time` double NOT NULL COMMENT '弹幕时间（秒）',
  `type` tinyint NOT NULL COMMENT '弹幕类型 1=普通 4=底部 5=顶部',
  `color` int NOT NULL COMMENT '弹幕颜色（ARGB整数）',
  `source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '弹幕来源',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `bgm_user_id` int NOT NULL COMMENT 'bangumi id',
  `c_id` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_time`(`time` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `danmaku_episode_id_index`(`episode_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2054175879880593429 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '弹幕表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for forum_comment
-- ----------------------------
DROP TABLE IF EXISTS `forum_comment`;
CREATE TABLE `forum_comment`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户id(一般时bangumi id)',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父评论ID；0表示一级评论，非0表示回复某条评论（楼中楼）',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评论内容',
  `image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图片地址',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '评论点赞数量',
  `ip_address` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发表评论时的IP地址',
  `create_time` datetime NOT NULL COMMENT '评论创建时间',
  `nickname` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `avatar_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `forum_comment_parent_id_index`(`parent_id` ASC) USING BTREE,
  INDEX `forum_comment_user_id_index`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '论坛评论' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL COMMENT '主键 ID（雪花）',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '邮箱',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'BCrypt 哈希密码',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '头像地址',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '昵称',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_email`(`email` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AnimeFlow 用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_oauth
-- ----------------------------
DROP TABLE IF EXISTS `user_oauth`;
CREATE TABLE `user_oauth`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` bigint NOT NULL COMMENT 'AnimeFlow 用户 ID',
  `platform` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '第三方平台，如 bangumi',
  `platform_uid` bigint NOT NULL COMMENT '第三方平台用户 ID',
  `access_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '第三方 access_token',
  `refresh_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '第三方 refresh_token',
  `expire_time` bigint NOT NULL COMMENT 'access_token 过期时间（Unix 秒）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1=有效，0=失效',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_platform`(`user_id` ASC, `platform` ASC) USING BTREE COMMENT '同一用户每个平台仅一条绑定',
  UNIQUE INDEX `uk_platform_uid`(`platform` ASC, `platform_uid` ASC) USING BTREE COMMENT '同一第三方账号仅绑定一个用户'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户第三方 OAuth 绑定' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bgm_subject
-- ----------------------------
DROP TABLE IF EXISTS `bgm_subject`;
CREATE TABLE `bgm_subject`  (
  `id` int NOT NULL COMMENT 'Bangumi 条目 ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '日文名',
  `name_cn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '中文名',
  `type` tinyint NOT NULL COMMENT '条目类型 1=书籍 2=动画 3=音乐 4=游戏 6=三次元',
  `info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '条目信息摘要',
  `rating_rank` int NOT NULL DEFAULT 0 COMMENT '评分排名',
  `rating_score` decimal(4, 2) NOT NULL DEFAULT 0.00 COMMENT '评分',
  `rating_total` int NOT NULL DEFAULT 0 COMMENT '评分人数',
  `rating_count` json NULL COMMENT '各分值人数分布 [1-10星]',
  `locked` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否锁定 0=否 1=是',
  `nsfw` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否NSFW 0=否 1=是',
  `img_large` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '大图封面 URL',
  `img_common` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '普通封面 URL',
  `img_medium` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '中图封面 URL',
  `img_small` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '小图封面 URL',
  `img_grid` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '网格封面 URL',
  `sync_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后同步时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次入库时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_rating_score`(`rating_score` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 条目表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_bgm_collection
-- ----------------------------
DROP TABLE IF EXISTS `user_bgm_collection`;
CREATE TABLE `user_bgm_collection`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` bigint NOT NULL COMMENT 'AnimeFlow 用户 ID，关联 user.id',
  `subject_id` int NOT NULL COMMENT 'Bangumi 条目 ID，关联 bgm_subject.id',
  `bgm_interest_id` bigint NOT NULL COMMENT 'Bangumi 收藏记录 ID（interest.id）',
  `rate` tinyint NOT NULL DEFAULT 0 COMMENT '用户评分 0-10，0 表示未评分',
  `type` tinyint NOT NULL COMMENT '收藏类型 1=想看 2=看过 3=在看 4=搁置 5=抛弃',
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '收藏评论',
  `tags` json NULL COMMENT '收藏标签 JSON 数组',
  `bgm_updated_at` bigint NOT NULL COMMENT 'Bangumi 收藏更新时间 Unix 秒（interest.updatedAt）',
  `sync_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后同步时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次入库时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_bgm_interest_id`(`bgm_interest_id` ASC) USING BTREE COMMENT 'Bangumi 收藏 ID 唯一',
  UNIQUE INDEX `uk_user_subject`(`user_id` ASC, `subject_id` ASC) USING BTREE COMMENT '同一用户对同一条目仅一条收藏',
  INDEX `idx_user_type`(`user_id` ASC, `type` ASC) USING BTREE,
  INDEX `idx_subject_id`(`subject_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户 Bangumi 收藏关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_character
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_character`;
CREATE TABLE `bangumi_character`  (
  `id` int UNSIGNED NOT NULL COMMENT '角色 ID',
  `role` tinyint UNSIGNED NOT NULL COMMENT '角色类型：1角色 2机体 3组织',
  `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '角色名',
  `infobox` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始 wiki 字符串',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '角色简介',
  `comments` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论/吐槽数',
  `collects` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 角色' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_episode
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_episode`;
CREATE TABLE `bangumi_episode`  (
  `id` int UNSIGNED NOT NULL COMMENT '章节 ID',
  `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '章节名称',
  `name_cn` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '章节简体中文名',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节介绍',
  `airdate` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '播出时间',
  `disc` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '所在光盘序号',
  `duration` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '播放时长',
  `subject_id` int UNSIGNED NOT NULL COMMENT '作品 ID',
  `sort` smallint UNSIGNED NOT NULL DEFAULT 0 COMMENT '集数排序',
  `type` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '类型：0正篇 1特别篇 2OP 3ED 4Trailer 5MAD 6其他',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_subject_id`(`subject_id` ASC) USING BTREE,
  INDEX `idx_subject_sort`(`subject_id` ASC, `sort` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 章节' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_person
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_person`;
CREATE TABLE `bangumi_person`  (
  `id` int UNSIGNED NOT NULL COMMENT '人物 ID',
  `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '人物名',
  `type` tinyint UNSIGNED NOT NULL COMMENT '类型：1个人 2公司 3组合',
  `career` json NULL COMMENT '人物职业列表',
  `infobox` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始 wiki 字符串',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '人物简介',
  `comments` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论/吐槽数',
  `collects` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 人物' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_subject
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_subject`;
CREATE TABLE `bangumi_subject`  (
  `id` int UNSIGNED NOT NULL COMMENT '条目 ID',
  `type` tinyint UNSIGNED NOT NULL COMMENT '作品类型：1漫画 2动画 3音乐 4游戏 6三次元',
  `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '条目名',
  `name_cn` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '条目简体中文名',
  `infobox` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始 wiki 字符串',
  `platform` smallint UNSIGNED NOT NULL DEFAULT 0 COMMENT '条目平台',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '条目简介',
  `nsfw` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否 NSFW',
  `tags` json NULL COMMENT '标签列表 [{name,count}]',
  `meta_tags` json NULL COMMENT '公共标签列表',
  `score` decimal(4, 1) NULL DEFAULT NULL COMMENT '评分',
  `score_details` json NULL COMMENT '评分分布 {1..10: count}',
  `rank` int UNSIGNED NULL DEFAULT NULL COMMENT '类别内排名',
  `date` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '发行日期',
  `favorite` json NULL COMMENT '收藏状态 {wish,done,doing,on_hold,dropped}',
  `series` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为系列作品',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_bgm_rank`(`rank` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 条目' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_person_character
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_person_character`;
CREATE TABLE `bangumi_person_character`  (
  `person_id` int UNSIGNED NOT NULL COMMENT '人物 ID',
  `subject_id` int UNSIGNED NOT NULL COMMENT '条目 ID',
  `character_id` int UNSIGNED NOT NULL COMMENT '角色 ID',
  `type` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联类型',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '概要',
  PRIMARY KEY (`person_id`, `subject_id`, `character_id`) USING BTREE,
  INDEX `idx_subject_id`(`subject_id` ASC) USING BTREE,
  INDEX `idx_character_id`(`character_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 人物-角色关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_person_relation
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_person_relation`;
CREATE TABLE `bangumi_person_relation`  (
  `person_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'prsn 现实人物 / crt 虚拟角色',
  `person_id` int UNSIGNED NOT NULL COMMENT '人物/角色 ID',
  `related_person_id` int UNSIGNED NOT NULL COMMENT '关联人物/角色 ID',
  `relation_type` smallint UNSIGNED NOT NULL COMMENT '关联类型',
  `spoiler` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否剧透',
  `ended` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已结束',
  PRIMARY KEY (`person_type`, `person_id`, `related_person_id`, `relation_type`) USING BTREE,
  INDEX `idx_person`(`person_type` ASC, `person_id` ASC) USING BTREE,
  INDEX `idx_related`(`person_type` ASC, `related_person_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 人物/角色关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_subject_character
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_subject_character`;
CREATE TABLE `bangumi_subject_character`  (
  `character_id` int UNSIGNED NOT NULL COMMENT '角色 ID',
  `subject_id` int UNSIGNED NOT NULL COMMENT '作品 ID',
  `type` tinyint UNSIGNED NOT NULL COMMENT '角色类型：1主角 2配角 3客串',
  `order` smallint UNSIGNED NOT NULL DEFAULT 0 COMMENT '角色列表排序',
  PRIMARY KEY (`subject_id`, `character_id`, `type`, `order`) USING BTREE,
  INDEX `idx_character_id`(`character_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 条目-角色关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_subject_person
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_subject_person`;
CREATE TABLE `bangumi_subject_person`  (
  `person_id` int UNSIGNED NOT NULL COMMENT '人物 ID',
  `subject_id` int UNSIGNED NOT NULL COMMENT '作品 ID',
  `position` smallint UNSIGNED NOT NULL COMMENT '担任职位',
  `appear_eps` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '参与章节',
  PRIMARY KEY (`person_id`, `subject_id`, `position`) USING BTREE,
  INDEX `idx_subject_id`(`subject_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 条目-人物关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for bangumi_subject_relation
-- ----------------------------
DROP TABLE IF EXISTS `bangumi_subject_relation`;
CREATE TABLE `bangumi_subject_relation`  (
  `subject_id` int UNSIGNED NOT NULL COMMENT '作品 ID',
  `relation_type` smallint UNSIGNED NOT NULL COMMENT '关联类型',
  `related_subject_id` int UNSIGNED NOT NULL COMMENT '关联作品 ID',
  `order` smallint UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联排序',
  PRIMARY KEY (`subject_id`, `relation_type`, `related_subject_id`, `order`) USING BTREE,
  INDEX `idx_related_subject_id`(`related_subject_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Bangumi 条目关联' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
