package com.fireworks.service.impl;

import com.fireworks.exception.BusinessException;
import com.fireworks.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件存储实现（用于本地开发/兜底）
 */
@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    @Value("${app.storage.local.upload-path:D:/Fireworks/picture/}")
    private String uploadPath;

    @Value("${app.storage.local.url-prefix:/uploads/}")
    private String urlPrefix;

    @Override
    public String save(String subDir, String filename, byte[] bytes) {
        try {
            String safeSubDir = normalizeSubDir(subDir);
            Path dir = Paths.get(uploadPath, safeSubDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            Path filePath = dir.resolve(filename);
            Files.write(filePath, bytes);
            String url = normalizeUrlPrefix(urlPrefix) + safeSubDir + filename;
            log.info("Saved file: {} -> {}", filePath.toAbsolutePath(), url);
            return url;
        } catch (IOException e) {
            log.error("Save file failed", e);
            throw new BusinessException(500, "文件保存失败");
        }
    }

    private String normalizeSubDir(String subDir) {
        if (subDir == null || subDir.isBlank()) {
            return "";
        }
        String normalized = subDir.trim().replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

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

