package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.dto.CreateProductRequest;
import com.fireworks.dto.UpdateProductRequest;
import com.fireworks.dto.VideoExtractResult;
import com.fireworks.enums.VideoExtractStatus;
import com.fireworks.service.ProductService;
import com.fireworks.service.VideoExtractService;
import com.fireworks.vo.PageVO;
import com.fireworks.vo.ProductVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理控制器（管理端）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final VideoExtractService videoExtractService;

    /**
     * 创建商品
     *
     * @param request 创建商品请求
     * @return 创建的商品信息
     */
    @PostMapping
    public Result<ProductVO> createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("创建商品: name={}", request.getName());
        ProductVO productVO = productService.createProduct(request);
        return Result.success("创建成功", productVO);
    }

    /**
     * 获取商品列表（管理端，需要认证）
     *
     * @param status 商品状态筛选（可选）：ON_SHELF-上架, OFF_SHELF-下架
     * @param page   页码（从1开始，默认1）
     * @param size   每页数量（默认20）
     * @return 分页商品列表
     */
    @GetMapping
    public Result<PageVO<ProductVO>> getProductList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.debug("获取商品列表: status={}, sort={}, page={}, size={}", status, sort, page, size);
        PageVO<ProductVO> pageVO = productService.getProductList(status, sort, page, size);
        return Result.success(pageVO);
    }

    /**
     * 获取商品详情
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public Result<ProductVO> getProductById(@PathVariable Long id) {
        log.debug("获取商品详情: id={}", id);
        ProductVO productVO = productService.getProductById(id);
        return Result.success(productVO);
    }

    /**
     * 更新商品
     *
     * @param id      商品ID
     * @param request 更新商品请求
     * @return 更新后的商品信息
     */
    @PutMapping("/{id}")
    public Result<ProductVO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        log.info("更新商品: id={}", id);
        ProductVO productVO = productService.updateProduct(id, request);
        return Result.success("更新成功", productVO);
    }

    /**
     * 删除商品（逻辑删除）
     *
     * @param id 商品ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        log.info("删除商品: id={}", id);
        productService.deleteProduct(id);
        return Result.success("删除成功", null);
    }

    /**
     * 提取商品视频URL（从二维码图片解析）
     *
     * @param id 商品ID
     * @return 更新后的商品信息
     */
    @PostMapping("/{id}/extract-video")
    public Result<ProductVO> extractVideo(@PathVariable Long id) {
        log.info("提取商品视频: id={}", id);

        // 获取商品信息
        ProductVO product = productService.getProductById(id);
        if (product.getImages() == null || product.getImages().size() < 3) {
            return Result.error(400, "商品缺少二维码图片");
        }

        // 从第三张图片（二维码）提取视频URL
        String qrCodeImageUrl = product.getImages().get(2);
        VideoExtractResult result = videoExtractService.extractVideoFromQrCodeImage(qrCodeImageUrl);
        VideoExtractStatus status = result != null ? result.getStatus() : null;
        String statusText = status != null ? status.name() : VideoExtractStatus.FAILED.name();
        String message = result != null ? result.getMessage() : "无法从二维码提取视频链接";
        String targetUrl = result != null ? result.getTargetUrl() : null;
        String videoUrl = status == VideoExtractStatus.SUCCESS && result != null ? result.getVideoUrl() : product.getVideoUrl();

        ProductVO updatedProduct = productService.updateVideoExtractInfo(id, videoUrl, statusText, message, targetUrl);

        if (status != VideoExtractStatus.SUCCESS) {
            return Result.error(400, message);
        }

        return Result.success("视频提取成功", updatedProduct);
    }
}
