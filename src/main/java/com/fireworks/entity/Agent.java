package com.fireworks.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代理商实体类
 */
@Data
@TableName("agent")
public class Agent {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 代理商唯一编码 (A001-A999)
     */
    private String code;

    /**
     * 代理商名称
     */
    private String name;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 状态: ACTIVE-启用, DISABLED-禁用
     */
    private String status;

    /**
     * 小程序码图片URL
     */
    private String qrcodeUrl;

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
