package com.fireworks.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fireworks.exception.BusinessException;
import com.fireworks.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信云托管对象存储实现
 *
 * <p>在微信云托管环境中通过云调用上传文件到对象存储。</p>
 *
 * <p>流程：</p>
 * <ol>
 *   <li>调用 /tcb/uploadfile 获取上传凭证</li>
 *   <li>使用凭证上传文件到 COS</li>
 *   <li>返回 cloud:// 协议的文件 ID</li>
 * </ol>
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cloud")
public class WxCloudStorageServiceImpl implements FileStorageService {

    private static final String WX_CLOUD_BASE_URL = "http://api.weixin.qq.com";
    private static final String UPLOAD_FILE_PATH = "/tcb/uploadfile";

    @Value("${app.storage.cloud.env-id:}")
    private String envId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WxCloudStorageServiceImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String save(String subDir, String filename, byte[] bytes) {
        if (envId == null || envId.isBlank()) {
            log.error("WX_CLOUD_ENV_ID 环境变量未配置");
            throw new BusinessException(500, "云存储配置错误");
        }

        String cloudPath = normalizeCloudPath(subDir, filename);

        try {
            // Step 1: 获取上传凭证
            UploadCredential credential = getUploadCredential(cloudPath);

            // Step 2: 上传文件到 COS
            uploadToCos(credential, bytes);

            log.info("云存储上传成功: {} -> {}", cloudPath, credential.fileId);
            return credential.fileId;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("云存储上传失败: {}", cloudPath, e);
            throw new BusinessException(500, "文件上传失败");
        }
    }

    /**
     * 获取上传凭证
     */
    private UploadCredential getUploadCredential(String cloudPath) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("env", envId);
        params.put("path", cloudPath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                WX_CLOUD_BASE_URL + UPLOAD_FILE_PATH,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("获取上传凭证失败: status={}", response.getStatusCode());
            throw new BusinessException(500, "获取上传凭证失败");
        }

        JsonNode json = objectMapper.readTree(response.getBody());

        // 检查错误码
        if (json.has("errcode") && json.get("errcode").asInt() != 0) {
            String errmsg = json.has("errmsg") ? json.get("errmsg").asText() : "未知错误";
            log.error("获取上传凭证失败: errcode={}, errmsg={}", json.get("errcode").asInt(), errmsg);
            throw new BusinessException(500, "获取上传凭证失败: " + errmsg);
        }

        UploadCredential credential = new UploadCredential();
        credential.url = json.get("url").asText();
        credential.token = json.get("token").asText();
        credential.authorization = json.get("authorization").asText();
        credential.fileId = json.get("file_id").asText();
        credential.cosFileId = json.get("cos_file_id").asText();

        return credential;
    }

    /**
     * 上传文件到 COS
     */
    private void uploadToCos(UploadCredential credential, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("Authorization", credential.authorization);
        headers.set("x-cos-security-token", credential.token);
        headers.set("x-cos-meta-fileid", credential.cosFileId);

        HttpEntity<byte[]> request = new HttpEntity<>(bytes, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                credential.url,
                HttpMethod.PUT,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("COS 上传失败: status={}, body={}", response.getStatusCode(), response.getBody());
            throw new BusinessException(500, "文件上传到 COS 失败");
        }
    }

    /**
     * 规范化云存储路径
     */
    private String normalizeCloudPath(String subDir, String filename) {
        StringBuilder sb = new StringBuilder();

        if (subDir != null && !subDir.isBlank()) {
            String normalized = subDir.trim().replace("\\", "/");
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (!normalized.isEmpty()) {
                sb.append(normalized);
                if (!normalized.endsWith("/")) {
                    sb.append("/");
                }
            }
        }

        sb.append(filename);
        return sb.toString();
    }

    /**
     * 上传凭证
     */
    private static class UploadCredential {
        String url;
        String token;
        String authorization;
        String fileId;
        String cosFileId;
    }
}
