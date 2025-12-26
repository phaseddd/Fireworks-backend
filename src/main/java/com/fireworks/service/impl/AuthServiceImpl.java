package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fireworks.dto.LoginRequest;
import com.fireworks.entity.Admin;
import com.fireworks.exception.BusinessException;
import com.fireworks.mapper.AdminMapper;
import com.fireworks.service.AuthService;
import com.fireworks.util.JwtTokenProvider;
import com.fireworks.util.PasswordUtil;
import com.fireworks.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AdminMapper adminMapper;
    private final PasswordUtil passwordUtil;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 最大登录失败次数
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * 账号锁定时间（分钟）
     */
    private static final int LOCK_DURATION_MINUTES = 15;

    @Override
    @Transactional
    public LoginVO login(LoginRequest request) {
        // 1. 查询管理员
        Admin admin = adminMapper.selectOne(
                new LambdaQueryWrapper<Admin>()
                        .eq(Admin::getUsername, request.getUsername())
        );

        if (admin == null) {
            log.warn("登录失败: 用户名 {} 不存在", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 2. 检查账号是否被锁定
        if (isAccountLocked(admin)) {
            log.warn("登录失败: 账号 {} 已被锁定", request.getUsername());
            throw new BusinessException(403, "账号已锁定，请" + LOCK_DURATION_MINUTES + "分钟后重试");
        }

        // 3. 验证密码
        if (!passwordUtil.matches(request.getPassword(), admin.getPasswordHash())) {
            // 记录登录失败
            handleFailedLogin(admin);
            log.warn("登录失败: 用户名 {} 密码错误", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 4. 登录成功，重置失败次数
        resetFailedAttempts(admin);

        // 5. 生成 JWT Token
        String token = jwtTokenProvider.generateToken(admin.getUsername());
        long expiresIn = jwtTokenProvider.getExpiration() / 1000; // 转换为秒

        log.info("管理员 {} 登录成功", admin.getUsername());
        return new LoginVO(token, expiresIn);
    }

    /**
     * 检查账号是否被锁定
     */
    private boolean isAccountLocked(Admin admin) {
        if (admin.getLockUntil() == null) {
            return false;
        }
        if (LocalDateTime.now().isBefore(admin.getLockUntil())) {
            return true;
        }
        // 锁定时间已过，重置状态
        resetFailedAttempts(admin);
        return false;
    }

    /**
     * 处理登录失败
     */
    private void handleFailedLogin(Admin admin) {
        int failedAttempts = (admin.getFailedAttempts() == null ? 0 : admin.getFailedAttempts()) + 1;

        LambdaUpdateWrapper<Admin> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Admin::getId, admin.getId())
                .set(Admin::getFailedAttempts, failedAttempts);

        // 达到最大失败次数，锁定账号
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
            updateWrapper.set(Admin::getLockUntil, lockUntil);
            log.warn("账号 {} 登录失败 {} 次，已锁定至 {}", admin.getUsername(), failedAttempts, lockUntil);
        }

        adminMapper.update(null, updateWrapper);
    }

    /**
     * 重置登录失败次数
     */
    private void resetFailedAttempts(Admin admin) {
        if (admin.getFailedAttempts() != null && admin.getFailedAttempts() > 0) {
            adminMapper.update(null,
                    new LambdaUpdateWrapper<Admin>()
                            .eq(Admin::getId, admin.getId())
                            .set(Admin::getFailedAttempts, 0)
                            .set(Admin::getLockUntil, null)
            );
        }
    }
}
