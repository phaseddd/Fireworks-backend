package com.fireworks.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                // 拦截管理端 API
                .addPathPatterns("/api/v1/admin/**")
                .addPathPatterns("/api/v1/products/**")
                .addPathPatterns("/api/v1/agents/**")
                .addPathPatterns("/api/v1/inquiries/**")
                // 排除公开接口
                .excludePathPatterns("/api/v1/auth/**")
                .excludePathPatterns("/api/health")
                .excludePathPatterns("/api/")
                // 排除客户端公开接口（商品浏览）
                .excludePathPatterns("/api/v1/products/public/**");
    }
}
