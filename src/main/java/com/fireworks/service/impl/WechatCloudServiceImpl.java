package com.fireworks.service.impl;

import com.fireworks.exception.BusinessException;
import com.fireworks.service.WechatCloudService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信云调用服务实现
 *
 * <p>在微信云托管环境中可通过云调用内网地址免鉴权调用微信 OpenAPI。</p>
 */
@Slf4j
@Service
public class WechatCloudServiceImpl implements WechatCloudService {

    private static final String WX_CLOUD_BASE_URL = "http://api.weixin.qq.com";
    private static final String WXACODE_UNLIMIT_PATH = "/wxa/getwxacodeunlimit";

    private final RestTemplate restTemplate;

    @Value("${app.wechat.wxa-code.env-version:release}")
    private String envVersion;

    public WechatCloudServiceImpl() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(WX_CLOUD_BASE_URL));
    }

    @Override
    public byte[] generateWxaCode(String scene, String page) {
        if (scene == null || scene.isBlank()) {
            throw new BusinessException(400, "scene 不能为空");
        }
        if (page == null || page.isBlank()) {
            throw new BusinessException(400, "page 不能为空");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("scene", scene);
        params.put("page", page);
        params.put("env_version", normalizeEnvVersion(envVersion));
        params.put("width", 430);
        params.put("auto_color", false);
        params.put("line_color", Map.of("r", 255, "g", 72, "b", 0));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.postForEntity(
                    WXACODE_UNLIMIT_PATH,
                    request,
                    byte[].class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(500, "生成小程序码失败");
            }

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessException(500, "生成小程序码失败");
            }

            // 云调用失败时可能返回 JSON（包含 errcode/errmsg）
            if (looksLikeJson(body)) {
                String json = new String(body, StandardCharsets.UTF_8);
                log.warn("wxacode.getUnlimited 返回 JSON: {}", json);
                throw new BusinessException(500, "生成小程序码失败");
            }

            return body;
        } catch (RestClientException e) {
            log.error("调用 wxacode.getUnlimited 失败", e);
            throw new BusinessException(500, "生成小程序码失败");
        }
    }

    private boolean looksLikeJson(byte[] body) {
        int i = 0;
        while (i < body.length && (body[i] == ' ' || body[i] == '\n' || body[i] == '\r' || body[i] == '\t')) {
            i++;
        }
        return i < body.length && body[i] == '{';
    }

    private String normalizeEnvVersion(String value) {
        if (value == null || value.isBlank()) {
            return "release";
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "release", "trial", "develop" -> v;
            default -> "release";
        };
    }
}
