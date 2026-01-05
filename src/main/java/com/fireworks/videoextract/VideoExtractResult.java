package com.fireworks.videoextract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频提取结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoExtractResult {

    /**
     * 提取到的视频直链（mp4/m3u8）
     */
    private String videoUrl;

    /**
     * 提取状态
     */
    private VideoExtractStatus status;

    /**
     * 提取说明/失败原因
     */
    private String message;

    /**
     * 目标网址（H5/二维码URL），用于后续补充规则
     */
    private String targetUrl;
}
