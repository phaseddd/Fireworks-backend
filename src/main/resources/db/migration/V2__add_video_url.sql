-- ==========================================
-- V2: 添加视频URL字段
-- 用于存储燃放效果视频的直接链接
-- ==========================================

ALTER TABLE `product` ADD COLUMN `video_url` VARCHAR(500) COMMENT '燃放效果视频URL' AFTER `images`;
