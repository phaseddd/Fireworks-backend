package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fireworks.entity.Product;
import com.fireworks.mapper.ProductMapper;
import com.fireworks.videoextract.VideoExtractResult;
import com.fireworks.videoextract.VideoExtractStatus;
import com.fireworks.service.ProductVideoExtractAsyncService;
import com.fireworks.service.VideoExtractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 商品视频异步提取任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVideoExtractAsyncServiceImpl implements ProductVideoExtractAsyncService {

    private final ProductMapper productMapper;
    private final VideoExtractService videoExtractService;

    @Async("videoExtractExecutor")
    @Override
    public void extractAndUpdate(Long productId, String qrCodeImageUrl, boolean resetVideoUrl) {
        if (productId == null) {
            return;
        }

        if (!StringUtils.hasText(qrCodeImageUrl)) {
            updateExtractInfo(productId, null, VideoExtractStatus.SKIPPED, "缺少二维码图片", null, resetVideoUrl);
            return;
        }

        updateExtractInfo(productId, null, VideoExtractStatus.RUNNING, "开始解析", null, resetVideoUrl);

        try {
            VideoExtractResult result = videoExtractService.extractVideoFromQrCodeImage(qrCodeImageUrl);
            applyResult(productId, result, resetVideoUrl);
        } catch (Exception e) {
            log.error("[视频提取] 异步解析异常: productId={}", productId, e);
            updateExtractInfo(productId, null, VideoExtractStatus.FAILED, "异步解析异常", null, resetVideoUrl);
        }
    }

    private void applyResult(Long productId, VideoExtractResult result, boolean resetVideoUrl) {
        if (result == null || result.getStatus() == null) {
            updateExtractInfo(productId, null, VideoExtractStatus.FAILED, "提取结果为空", null, resetVideoUrl);
            return;
        }

        String videoUrl = result.getStatus() == VideoExtractStatus.SUCCESS ? result.getVideoUrl() : null;
        updateExtractInfo(productId, videoUrl, result.getStatus(), result.getMessage(), result.getTargetUrl(), resetVideoUrl);
    }

    private void updateExtractInfo(
            Long productId,
            String videoUrl,
            VideoExtractStatus status,
            String message,
            String targetUrl,
            boolean resetVideoUrl
    ) {
        LambdaUpdateWrapper<Product> update = new LambdaUpdateWrapper<>();
        update.eq(Product::getId, productId)
                .set(Product::getVideoExtractStatus, status != null ? status.name() : null)
                .set(Product::getVideoExtractMessage, message)
                .set(Product::getVideoExtractTargetUrl, targetUrl)
                .set(status == VideoExtractStatus.SUCCESS || resetVideoUrl, Product::getVideoUrl, videoUrl);

        int rows = productMapper.update(null, update);
        if (rows <= 0) {
            log.warn("[视频提取] 更新失败: productId={}, status={}", productId, status);
        }
    }
}
