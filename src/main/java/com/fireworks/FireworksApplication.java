package com.fireworks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Fireworks 烟花商品展示小程序 - 后端服务启动类
 *
 * @author Fireworks Team
 * @since 2025-12-26
 */
@SpringBootApplication
@EnableAsync
public class FireworksApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireworksApplication.class, args);
    }

}
