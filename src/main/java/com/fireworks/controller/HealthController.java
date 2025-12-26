package com.fireworks.controller;

import com.fireworks.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口
 * 用于微信云托管健康检查和基础连通性测试
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("service", "fireworks-backend");
        data.put("timestamp", LocalDateTime.now());
        return Result.success(data);
    }

    @GetMapping("/")
    public Result<String> index() {
        return Result.success("Fireworks Backend Service is running! 🎆");
    }
}
