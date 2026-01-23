-- ========== 1. 新建分类表 ==========
CREATE TABLE category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========== 2. 插入初始分类数据 ==========
INSERT INTO category (name, status) VALUES
('礼花类', 'ACTIVE'),
('烟花类', 'ACTIVE'),
('鞭炮类', 'ACTIVE'),
('组合类', 'ACTIVE'),
('其他', 'ACTIVE');

-- ========== 3. Product 表新增 category_id 字段 ==========
ALTER TABLE product
ADD COLUMN category_id BIGINT AFTER category;
-- 注意：无外键约束

-- ========== 4. 数据迁移：根据原 category 字段值映射到 category_id ==========
-- 支持两种格式：
--   1. 新数据：category 直接存储分类名称（如 '礼花类'）
--   2. 旧数据：category 存储枚举值（如 'GIFT'）需要转换

-- 先尝试直接匹配分类名称（新数据格式）
UPDATE product p
SET category_id = (SELECT id FROM category c WHERE c.name = p.category)
WHERE category_id IS NULL
  AND EXISTS (SELECT 1 FROM category c WHERE c.name = p.category);

-- 再处理枚举值格式（旧数据格式）
UPDATE product SET category_id = (
    SELECT id FROM category WHERE name = CASE product.category
        WHEN 'GIFT' THEN '礼花类'
        WHEN 'FIREWORK' THEN '烟花类'
        WHEN 'FIRECRACKER' THEN '鞭炮类'
        WHEN 'COMBO' THEN '组合类'
        WHEN 'OTHER' THEN '其他'
        ELSE NULL
    END
)
WHERE category_id IS NULL;

-- ========== 5. 同步更新 category 字段为分类名称（去除枚举值） ==========
UPDATE product p
JOIN category c ON p.category_id = c.id
SET p.category = c.name
WHERE p.category IN ('GIFT', 'FIREWORK', 'FIRECRACKER', 'COMBO', 'OTHER');

-- ========== 6. 可选：迁移完成验证后可移除旧字段 ==========
-- ALTER TABLE product DROP COLUMN category;
