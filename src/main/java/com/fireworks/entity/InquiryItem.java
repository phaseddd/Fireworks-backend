package com.fireworks.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 询价商品关联实体类
 */
@Data
@TableName("inquiry_item")
public class InquiryItem {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 询价记录ID
     */
    private Long inquiryId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 商品数量
     */
    private Integer quantity;

    /**
     * 商品信息 (非数据库字段，用于关联查询)
     */
    @TableField(exist = false)
    private Product product;
}
