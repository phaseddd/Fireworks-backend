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
     * 商品分类
     */
    private String category;

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
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为 VO
     */
    public static ProductVO fromEntity(Product product) {
        if (product == null) {
            return null;
        }
        return ProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .category(product.getCategory())
                .description(product.getDescription())
                .stock(product.getStock())
                .status(product.getStatus())
                .images(product.getImages())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
