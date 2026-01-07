package com.fireworks.service;

/**
 * 文件存储服务（用于后端生成的文件落地，例如代理商小程序码）
 */
public interface FileStorageService {

    /**
     * 保存文件并返回可访问 URL
     *
     * @param subDir   子目录（如 "qrcode/"）
     * @param filename 文件名（如 "A001.png"）
     * @param bytes    文件内容
     * @return 可访问 URL（如 "/uploads/qrcode/A001.png"）
     */
    String save(String subDir, String filename, byte[] bytes);
}

