package com.fireworks.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fireworks.exception.BusinessException;
import com.fireworks.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 微信云托管对象存储实现
 *
 * <p>在微信云托管环境中通过云调用上传文件到对象存储。</p>
 *
 * <p>流程：</p>
 * <ol>
 *   <li>调用 /tcb/uploadfile 获取上传凭证</li>
 *   <li>使用 OkHttp 上传文件到 COS（multipart/form-data）</li>
 *   <li>返回 cloud:// 协议的文件 ID</li>
 * </ol>
 */
@Slf4j
@Service
@Profile("prod")
public class WxCloudStorageServiceImpl implements FileStorageService {

    private static final String WX_CLOUD_BASE_URL = "http://api.weixin.qq.com";
    private static final String UPLOAD_FILE_PATH = "/tcb/uploadfile";

    @Value("${app.storage.cloud.env-id:}")
    private String envId;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WxCloudStorageServiceImpl() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
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
            uploadToCos(credential, bytes, cloudPath);

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
        String jsonBody = objectMapper.writeValueAsString(
                java.util.Map.of("env", envId, "path", cloudPath)
        );

        Request request = new Request.Builder()
                .url(WX_CLOUD_BASE_URL + UPLOAD_FILE_PATH)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("获取上传凭证失败: status={}", response.code());
                throw new BusinessException(500, "获取上传凭证失败");
            }

            String responseBody = response.body().string();
            JsonNode json = objectMapper.readTree(responseBody);

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

            log.debug("获取上传凭证成功: url={}", credential.url);
            return credential;
        }
    }

    /**
     * 上传文件到 COS (使用 OkHttp multipart/form-data)
     *
     * <p>腾讯云 COS POST 上传要求：</p>
     * <ul>
     *   <li>必须包含 key, Signature, x-cos-security-token, x-cos-meta-fileid 字段</li>
     *   <li>file 字段必须放在表单最后</li>
     * </ul>
     */
    private void uploadToCos(UploadCredential credential, byte[] bytes, String cloudPath) throws IOException {
        String filename = cloudPath.substring(cloudPath.lastIndexOf('/') + 1);

        // 使用 OkHttp 构建 multipart 请求
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", cloudPath)
                .addFormDataPart("Signature", credential.authorization)
                .addFormDataPart("x-cos-security-token", credential.token)
                .addFormDataPart("x-cos-meta-fileid", credential.cosFileId)
                .addFormDataPart("file", filename,
                        RequestBody.create(bytes, MediaType.parse("application/octet-stream")))
                .build();

        Request request = new Request.Builder()
                .url(credential.url)
                .post(requestBody)
                .build();

        log.debug("上传到 COS: url={}, path={}", credential.url, cloudPath);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("COS 上传失败: status={}, body={}", response.code(), responseBody);
                throw new BusinessException(500, "文件上传到 COS 失败: " + response.code());
            }

            log.debug("COS 上传成功: status={}", response.code());
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
