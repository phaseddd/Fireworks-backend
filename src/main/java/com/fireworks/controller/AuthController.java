package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.dto.LoginRequest;
import com.fireworks.service.AuthService;
import com.fireworks.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 管理员登录
     * @param request 登录请求
     * @return 登录响应（含 Token）
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        LoginVO loginVO = authService.login(request);
        return Result.success(loginVO);
    }
}
