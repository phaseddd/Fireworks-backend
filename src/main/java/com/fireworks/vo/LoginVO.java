package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应 VO
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /**
     * JWT Token
     */
    private String token;

    /**
     * Token 过期时间（秒）
     */
    private Long expiresIn;
}
