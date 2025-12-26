package com.fireworks.service;

import com.fireworks.dto.LoginRequest;
import com.fireworks.vo.LoginVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 管理员登录
     * @param request 登录请求
     * @return 登录响应（含 Token）
     */
    LoginVO login(LoginRequest request);
}
