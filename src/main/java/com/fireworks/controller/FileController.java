package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传控制器 (本地开发环境使用)
 */
@RestController
@RequestMapping("/api/v1/upload")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    /**
     * 本地上传物理路径 (例如: D:/Fireworks/picture/)
     */
    @Value("${app.storage.local.upload-path:D:/Fireworks/picture/}")
    private String uploadPath;

    /**
     * HTTP URL 访问前缀 (例如: /uploads/)
     */
    @Value("${app.storage.local.url-prefix:/uploads/}")
    private String urlPrefix;

    /**
     * 允许的图片类型
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    /**
     * 最大文件大小: 2MB
     */
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    /**
     * 上传图片
     *
     * @param file 图片文件
     * @param slot 图片槽位 (main/detail/qrcode)
     * @return 上传结果
     */
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "slot", required = false, defaultValue = "image") String slot
    ) {
        log.info("Uploading image: originalName={}, size={}, slot={}",
                file.getOriginalFilename(), file.getSize(), slot);

        // 验证文件非空
        if (file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("只支持 JPG/PNG/WebP/GIF 格式的图片");
        }

        // 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("图片大小不能超过 2MB");
        }

        try {
            // 根据槽位确定子目录: main -> main/, detail -> detail/, qrcode -> qrcode/
            String subDir = getSubDirectory(slot);

            // 确保上传目录存在 (包含子目录)
            Path uploadDir = Paths.get(uploadPath, subDir);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("Created upload directory: {}", uploadDir.toAbsolutePath());
            }

            // 生成唯一文件名: {timestamp}_{random}.{ext}
            String ext = getFileExtension(file.getOriginalFilename());
            String filename = String.format("%d_%s.%s",
                    System.currentTimeMillis(),
                    java.util.UUID.randomUUID().toString().substring(0, 8),
                    ext);

            // 保存文件到子目录
            Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath.toFile());
            log.info("File saved: {}", filePath.toAbsolutePath());

            // 返回访问 URL (包含子目录路径)
            // 例如: /uploads/main/1703836800000_abc12345.jpg
            String relativePath = subDir + filename;
            String url = normalizeUrlPrefix(urlPrefix) + relativePath;
            return Result.success(Map.of(
                    "url", url,
                    "filename", relativePath
            ));

        } catch (IOException e) {
            log.error("Failed to upload file", e);
            throw new BusinessException("文件上传失败，请重试");
        }
    }

    /**
     * 根据槽位获取子目录名
     * main -> main/
     * detail -> detail/
     * qrcode -> qrcode/
     * 其他 -> misc/
     */
    private String getSubDirectory(String slot) {
        if (slot == null || slot.isEmpty()) {
            return "misc/";
        }
        return switch (slot.toLowerCase()) {
            case "main" -> "main/";
            case "detail" -> "detail/";
            case "qrcode" -> "qrcode/";
            default -> "misc/";
        };
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "jpg";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).toLowerCase();
        }
        return "jpg";
    }

    /**
     * 规范化 URL 前缀，避免配置为 "/uploads" 时拼接出错
     * - 确保以 "/" 开头
     * - 确保以 "/" 结尾
     */
    private String normalizeUrlPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/uploads/";
        }
        String normalized = prefix.trim().replace("\\", "/");
        if (!normalized.startsWith("/") && !normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "/" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized.replaceAll("/+", "/").replace("http:/", "http://").replace("https:/", "https://");
    }
}
