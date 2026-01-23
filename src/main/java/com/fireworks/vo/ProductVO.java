package com.fireworks.vo;

import com.fireworks.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO {

    /**
     * 商品ID
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品价格
     */
    private BigDecimal price;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 商品状态: ON_SHELF-上架, OFF_SHELF-下架
     */
    private String status;

    /**
     * 商品图片列表
     */
    private List<String> images;

    /**
     * 燃放效果视频URL
     */
    private String videoUrl;

    /**
     * 视频提取状态
     */
    private String videoExtractStatus;

    /**
     * 视频提取说明/失败原因
     */
    private String videoExtractMessage;

    /**
     * 视频提取目标网址（H5/二维码URL）
     */
    private String videoExtractTargetUrl;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为 VO
     * <p>
     * 注意：categoryName 直接取自 product.category 字段（数据库存储的分类名称）
     */
    public static ProductVO fromEntity(Product product) {
        if (product == null) {
            return null;
        }
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .categoryId(product.getCategoryId())
                .categoryName(product.getCategory())
                .description(product.getDescription())
                .stock(product.getStock())
                .status(product.getStatus())
                .images(product.getImages())
                .videoUrl(product.getVideoUrl())
                .videoExtractStatus(product.getVideoExtractStatus())
                .videoExtractMessage(product.getVideoExtractMessage())
                .videoExtractTargetUrl(product.getVideoExtractTargetUrl())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
