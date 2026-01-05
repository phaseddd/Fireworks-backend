package com.fireworks.service;

import com.fireworks.videoextract.VideoExtractResult;

/**
 * 视频提取服务接口
 * 用于从二维码图片中解析URL并提取视频链接
 */
public interface VideoExtractService {

    /**
     * 从二维码图片提取视频信息（包含状态、目标网址等）
     *
     * @param qrCodeImageUrl 二维码图片URL
     * @return 提取结果
     */
    VideoExtractResult extractVideoFromQrCodeImage(String qrCodeImageUrl);
}
