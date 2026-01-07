package com.fireworks.enums;

/**
 * 视频提取状态
 */
public enum VideoExtractStatus {

    /**
     * 跳过（缺少二维码图等）
     */
    SKIPPED,

    /**
     * 提取中
     */
    RUNNING,

    /**
     * 提取成功
     */
    SUCCESS,

    /**
     * 需要动态渲染/直打API规则（静态HTML无法提取）
     */
    NEED_DYNAMIC_RENDER,

    /**
     * 不支持（例如：非URL/公众号关注码等）
     */
    UNSUPPORTED,

    /**
     * 提取失败
     */
    FAILED
}
