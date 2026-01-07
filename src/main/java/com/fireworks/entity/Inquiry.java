package com.fireworks.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 询价记录实体类
 */
@Data
@TableName("inquiry")
public class Inquiry {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 代理商编码 (可为空，表示直接访问)
     */
    private String agentCode;

    /**
     * 分享码（用于分享卡片/分享详情页定位）
     */
    private String shareCode;

    /**
     * 客户手机号
     */
    private String phone;

    /**
     * 客户微信号 (可选)
     */
    private String wechat;

    /**
     * 微信用户openid (可选)
     */
    private String openid;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 询价商品列表 (非数据库字段，用于关联查询)
     */
    @TableField(exist = false)
    private List<InquiryItem> items;
}
