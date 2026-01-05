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
 * 商品视频异步提取服务实现
 * <p>
 * 负责异步执行视频提取任务，避免阻塞商品保存流程。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>接收商品ID和二维码图片URL</li>
 *   <li>调用 {@link VideoExtractService} 执行实际提取</li>
 *   <li>将提取结果更新到商品记录</li>
 * </ul>
 * <p>
 * 使用独立线程池 {@code videoExtractExecutor} 执行，防止并发过高拖垮实例。
 *
 * @see VideoExtractService 视频提取核心服务
 * @see ProductServiceImpl#createProduct 创建商品时触发
 * @see ProductServiceImpl#updateProduct 更新商品时触发（仅二维码图变更）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVideoExtractAsyncServiceImpl implements ProductVideoExtractAsyncService {

    private final ProductMapper productMapper;
    private final VideoExtractService videoExtractService;

    /**
     * 异步提取视频并更新商品信息
     * <p>
     * 执行流程：
     * <ol>
     *   <li>校验参数，缺少二维码图片则标记为 SKIPPED</li>
     *   <li>更新状态为 RUNNING，表示开始解析</li>
     *   <li>调用 {@link VideoExtractService#extractVideoFromQrCodeImage} 执行提取</li>
     *   <li>根据提取结果更新商品的视频URL和状态信息</li>
     * </ol>
     * <p>
     * 任何异常都会被捕获并记录，不会抛出到调用方，确保静默失败。
     *
     * @param productId       商品ID
     * @param qrCodeImageUrl  二维码图片URL（商品的第3张图片）
     * @param resetVideoUrl   是否重置视频URL（更新商品时为true，新建时为false）
     */
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

    /**
     * 应用提取结果到商品
     * <p>
     * 根据提取结果的状态决定是否更新视频URL：
     * <ul>
     *   <li>SUCCESS：更新 videoUrl 为提取到的视频地址</li>
     *   <li>其他状态：videoUrl 置为 null（或保留原值，取决于 resetVideoUrl）</li>
     * </ul>
     *
     * @param productId     商品ID
     * @param result        视频提取结果
     * @param resetVideoUrl 是否重置视频URL
     */
    private void applyResult(Long productId, VideoExtractResult result, boolean resetVideoUrl) {
        if (result == null || result.getStatus() == null) {
            updateExtractInfo(productId, null, VideoExtractStatus.FAILED, "提取结果为空", null, resetVideoUrl);
            return;
        }

        String videoUrl = result.getStatus() == VideoExtractStatus.SUCCESS ? result.getVideoUrl() : null;
        updateExtractInfo(productId, videoUrl, result.getStatus(), result.getMessage(), result.getTargetUrl(), resetVideoUrl);
    }

    /**
     * 更新商品的视频提取信息到数据库
     * <p>
     * 使用 MyBatis-Plus 的 LambdaUpdateWrapper 进行条件更新：
     * <ul>
     *   <li>videoExtractStatus：提取状态枚举的名称</li>
     *   <li>videoExtractMessage：状态描述信息</li>
     *   <li>videoExtractTargetUrl：目标网址（用于后续分析）</li>
     *   <li>videoUrl：仅在 SUCCESS 状态或 resetVideoUrl=true 时更新</li>
     * </ul>
     *
     * @param productId     商品ID
     * @param videoUrl      视频URL（成功时有值）
     * @param status        提取状态枚举
     * @param message       状态描述
     * @param targetUrl     目标网址
     * @param resetVideoUrl 是否强制更新 videoUrl 字段
     */
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
