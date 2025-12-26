package com.fireworks.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员实体类
 */
@Data
@TableName("admin")
public class Admin {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码哈希值 (BCrypt加密)
     */
    private String passwordHash;

    /**
     * 登录失败次数 (用于账户锁定)
     */
    private Integer failedAttempts;

    /**
     * 锁定时间 (登录失败5次后锁定15分钟)
     */
    private LocalDateTime lockUntil;

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
