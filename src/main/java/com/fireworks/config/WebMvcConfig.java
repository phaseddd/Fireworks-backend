package com.fireworks.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    /**
     * 存储类型: local / cloud
     */
    @Value("${app.storage.type:local}")
    private String storageType;

    /**
     * 本地存储物理路径 (仅 local 模式使用)
     */
    @Value("${app.storage.local.upload-path:D:/Fireworks/picture/}")
    private String uploadPath;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                // 拦截管理端 API
                .addPathPatterns("/api/v1/admin/**")
                .addPathPatterns("/api/v1/products/**")
                .addPathPatterns("/api/v1/agents/**")
                .addPathPatterns("/api/v1/inquiries/**")
                .addPathPatterns("/api/v1/upload/**")
                // 排除公开接口
                .excludePathPatterns("/api/v1/auth/**")
                .excludePathPatterns("/api/health")
                .excludePathPatterns("/api/")
                // 排除客户端公开接口（商品浏览）
                .excludePathPatterns("/api/v1/products/public/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 仅本地开发环境需要静态资源映射
        if ("local".equals(storageType)) {
            // 映射 /uploads/** 到配置的物理存储目录
            // 例如: /uploads/main/1703836800000_xxxxxxxx.jpg -> D:/Fireworks/picture/main/1703836800000_xxxxxxxx.jpg
            String resourceLocation = "file:" + uploadPath;
            if (!resourceLocation.endsWith("/")) {
                resourceLocation += "/";
            }
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations(resourceLocation);
        }
    }
}
