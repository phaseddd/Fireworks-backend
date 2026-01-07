package com.fireworks.service;

/**
 * 微信云调用服务
 */
public interface WechatCloudService {

    /**
     * 生成小程序码（wxacode.getUnlimited）
     *
     * @param scene 场景值（<= 32 chars），如 "a=A001"
     * @param page  页面路径，如 "pages/index/index"
     * @return 图片二进制（PNG）
     */
    byte[] generateWxaCode(String scene, String page);
}

