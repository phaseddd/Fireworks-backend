package com.fireworks.config;

import com.fireworks.util.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 当前请求用户名（线程本地变量）
     */
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取 Authorization 头
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("请求缺少有效的 Authorization 头: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token无效\",\"data\":null}");
            return false;
        }

        String token = authHeader.substring(7);

        // 验证 Token
        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("Token 验证失败: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token已过期或无效\",\"data\":null}");
            return false;
        }

        // 解析用户名并存储到线程本地变量
        String username = jwtTokenProvider.getUsernameFromToken(token);
        CURRENT_USER.set(username);
        log.debug("用户 {} 访问: {}", username, request.getRequestURI());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理线程本地变量
        CURRENT_USER.remove();
    }

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUser() {
        return CURRENT_USER.get();
    }
}
