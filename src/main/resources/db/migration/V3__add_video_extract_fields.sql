-- ==========================================
-- V3: 添加视频提取状态字段
-- 用于记录自动解析视频的结果与目标网址，便于后续补充规则
-- ==========================================

ALTER TABLE `product`
    ADD COLUMN `video_extract_status` VARCHAR(32) NULL COMMENT '视频提取状态' AFTER `video_url`,
    ADD COLUMN `video_extract_message` VARCHAR(500) NULL COMMENT '视频提取说明/失败原因' AFTER `video_extract_status`,
    ADD COLUMN `video_extract_target_url` VARCHAR(1000) NULL COMMENT '视频提取目标网址(H5/二维码URL)' AFTER `video_extract_message`;

