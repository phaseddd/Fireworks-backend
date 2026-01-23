package com.fireworks.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品实体类
 */
@Data
@TableName(value = "product", autoResultMap = true)
public class Product {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品价格
     */
    private BigDecimal price;

    /**
     * 分类名称（冗余存储，与 category 表的 name 字段同步）
     * <p>
     * 用于快速展示分类名称，避免每次查询关联表。
     * 当分类名称变更时，由 CategoryService 负责同步更新。
     */
    private String category;

    /**
     * 分类ID（关联 category 表）
     */
    private Long categoryId;


    /**
     * 商品描述
     */
    private String description;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 商品状态: ON_SHELF-上架, OFF_SHELF-下架
     */
    private String status;

    /**
     * 商品图片列表 (JSON数组存储)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /**
     * 燃放效果视频URL
     */
    private String videoUrl;

    /**
     * 视频提取状态
     */
    private String videoExtractStatus;

    /**
     * 视频提取说明/失败原因
     */
    private String videoExtractMessage;

    /**
     * 视频提取目标网址（H5/二维码URL）
     */
    private String videoExtractTargetUrl;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
