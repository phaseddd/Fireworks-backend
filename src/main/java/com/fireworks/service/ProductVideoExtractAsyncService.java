package com.fireworks.service;

/**
 * 商品视频异步提取任务
 */
public interface ProductVideoExtractAsyncService {

    /**
     * 解析二维码图片并更新商品的视频提取信息
     *
     * @param productId       商品ID
     * @param qrCodeImageUrl  二维码图片URL
     * @param resetVideoUrl   是否强制重置 videoUrl（非成功也写入null）
     */
    void extractAndUpdate(Long productId, String qrCodeImageUrl, boolean resetVideoUrl);
}

