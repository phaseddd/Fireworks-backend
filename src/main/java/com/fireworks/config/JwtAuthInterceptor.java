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
            // 部分接口允许在无 JWT 的情况下访问（由 Controller 内部进行 OpenID/权限校验）
            if (shouldBypassJwt(request)) {
                return true;
            }

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

    private boolean shouldBypassJwt(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // 客户端创建询价：不需要 JWT（生产环境通常由云托管注入 OpenID）
        if ("POST".equalsIgnoreCase(method) && "/api/v1/inquiries".equals(uri)) {
            return true;
        }

        // 分享详情页读取：支持管理员 JWT 或代理商 OpenID（无 JWT 时在 Controller 内部校验）
        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/v1/inquiries/share/")) {
            return true;
        }

        // 代理商绑定：不需要 JWT（使用 OpenID + bindCode）
        if ("POST".equalsIgnoreCase(method) && "/api/v1/agents/bind".equals(uri)) {
            return true;
        }

        return false;
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
