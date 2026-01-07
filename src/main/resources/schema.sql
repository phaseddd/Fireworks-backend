-- ==========================================
-- Fireworks 数据库初始化脚本
-- 数据库: fireworks
-- 字符集: utf8mb4
-- 排序规则: utf8mb4_unicode_ci
-- ==========================================

-- 如果数据库不存在则创建
-- CREATE DATABASE IF NOT EXISTS fireworks CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE fireworks;

-- ==========================================
-- 1. 管理员表
-- ==========================================
DROP TABLE IF EXISTS `admin`;
CREATE TABLE `admin` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希值(BCrypt)',
    `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT '登录失败次数',
    `lock_until` DATETIME NULL COMMENT '锁定截止时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';

-- ==========================================
-- 2. 商品表
-- ==========================================
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '商品名称',
    `price` DECIMAL(10,2) NOT NULL COMMENT '商品价格',
    `category` VARCHAR(50) NOT NULL COMMENT '商品分类: GIFT-礼花类, FIREWORK-烟花类, FIRECRACKER-鞭炮类, COMBO-组合类, OTHER-其他',
    `description` TEXT COMMENT '商品描述',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ON_SHELF' COMMENT '商品状态: ON_SHELF-上架, OFF_SHELF-下架',
    `images` JSON COMMENT '商品图片列表(JSON数组)',
    `video_url` VARCHAR(500) COMMENT '燃放效果视频URL',
    `video_extract_status` VARCHAR(32) NULL COMMENT '视频提取状态',
    `video_extract_message` VARCHAR(500) NULL COMMENT '视频提取说明/失败原因',
    `video_extract_target_url` VARCHAR(1000) NULL COMMENT '视频提取目标网址(H5/二维码URL)',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_category` (`category`),
    INDEX `idx_status` (`status`),
    INDEX `idx_deleted` (`deleted`),
    INDEX `idx_created_at` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- ==========================================
-- 3. 代理商表
-- ==========================================
DROP TABLE IF EXISTS `agent`;
CREATE TABLE `agent` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `code` VARCHAR(10) NOT NULL UNIQUE COMMENT '代理商唯一编码(A001-A999)',
    `name` VARCHAR(50) NOT NULL COMMENT '代理商名称',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, DISABLED-禁用',
    `qrcode_url` VARCHAR(255) COMMENT '小程序码图片URL',
    `openid` VARCHAR(100) NULL COMMENT '代理商绑定OpenID',
    `bind_code` VARCHAR(32) NULL COMMENT '一次性绑定码',
    `bind_code_expires_at` DATETIME NULL COMMENT '绑定码过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_code` (`code`),
    UNIQUE KEY `uk_openid` (`openid`),
    UNIQUE KEY `uk_bind_code` (`bind_code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代理商表';

-- ==========================================
-- 4. 询价表
-- ==========================================
DROP TABLE IF EXISTS `inquiry`;
CREATE TABLE `inquiry` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `agent_code` VARCHAR(10) COMMENT '代理商编码(可为空表示直接访问)',
    `share_code` VARCHAR(64) NOT NULL UNIQUE COMMENT '分享码',
    `phone` VARCHAR(20) NOT NULL COMMENT '客户手机号',
    `wechat` VARCHAR(50) COMMENT '客户微信号',
    `openid` VARCHAR(100) COMMENT '微信用户openid',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_agent_code` (`agent_code`),
    INDEX `idx_created_at` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='询价记录表';

-- ==========================================
-- 5. 询价商品关联表
-- ==========================================
DROP TABLE IF EXISTS `inquiry_item`;
CREATE TABLE `inquiry_item` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `inquiry_id` BIGINT NOT NULL COMMENT '询价记录ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '商品数量',
    INDEX `idx_inquiry_id` (`inquiry_id`),
    INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='询价商品关联表';

-- ==========================================
-- 6. 初始数据
-- ==========================================

-- 默认管理员账号 (用户名: admin, 密码: admin123)
-- BCrypt 加密后的密码哈希值 (使用 spring-security-crypto 生成)
INSERT INTO `admin` (`username`, `password_hash`) VALUES
('admin', '$2a$10$YxrzbFMvUdk7ZcdxqKO22OtXN/1IwiZPz9rWy3CzFM4yAQXAS5ZdW');

-- 示例商品数据 (可选，方便测试)
INSERT INTO `product` (`name`, `price`, `category`, `description`, `stock`, `status`, `images`) VALUES
('绚丽烟花-大吉大利', 188.00, 'FIREWORK', '108发大型烟花，燃放时间约60秒，色彩绚丽，适合节日庆祝', 50, 'ON_SHELF', '[]'),
('礼花弹-富贵满堂', 88.00, 'GIFT', '12发礼花弹，高空绽放，声音洪亮，象征富贵吉祥', 100, 'ON_SHELF', '[]'),
('鞭炮-万响红', 28.00, 'FIRECRACKER', '10000响鞭炮，传统红色包装，声音清脆连贯', 200, 'ON_SHELF', '[]'),
('组合烟花-年年有余', 388.00, 'COMBO', '包含烟花、礼花、鞭炮组合套装，适合家庭使用', 30, 'ON_SHELF', '[]'),
('小型烟花-童趣乐园', 18.00, 'OTHER', '儿童安全烟花，火花小，适合小朋友玩耍', 500, 'ON_SHELF', '[]');

-- 示例代理商数据 (可选，方便测试)
INSERT INTO `agent` (`code`, `name`, `phone`, `status`) VALUES
('A001', '旅行社张总', '13800138001', 'ACTIVE'),
('A002', '民宿李老板', '13800138002', 'ACTIVE');
