package com.fireworks.service;

/**
 * 视频提取服务接口
 * 用于从二维码图片中解析URL并提取视频链接
 */
public interface VideoExtractService {

    /**
     * 从图片URL解析二维码内容
     *
     * @param imageUrl 二维码图片URL
     * @return 二维码中包含的URL，解析失败返回null
     */
    String parseQrCode(String imageUrl);

    /**
     * 从H5页面URL提取视频直链
     *
     * @param h5Url H5页面URL (如: https://web.huchengfireworks.com/3qIEca/#/pages/media/index?id=217)
     * @return 视频MP4直链，提取失败返回null
     */
    String extractVideoUrl(String h5Url);

    /**
     * 从二维码图片直接提取视频URL（组合方法）
     *
     * @param qrCodeImageUrl 二维码图片URL
     * @return 视频MP4直链，提取失败返回null
     */
    String extractVideoFromQrCode(String qrCodeImageUrl);
}
