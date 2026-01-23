package com.fireworks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建商品请求 DTO
 */
@Data
public class CreateProductRequest {

    /**
     * 商品名称（必填）
     */
    @NotBlank(message = "商品名称不能为空")
    private String name;

    /**
     * 商品价格（必填，正数）
     */
    @NotNull(message = "商品价格不能为空")
    @Positive(message = "商品价格必须大于0")
    private BigDecimal price;

    /**
     * 商品分类名称
     * @deprecated 已废弃，使用 categoryId 替代。保留字段仅用于数据兼容。
     */
    @Deprecated
    private String category;

    /**
     * 分类ID（关联 category 表，推荐使用）
     */
    private Long categoryId;

    /**
     * 库存数量（默认0）
     */
    @PositiveOrZero(message = "库存数量不能为负数")
    private Integer stock = 0;

    /**
     * 商品描述（选填）
     */
    private String description;

    /**
     * 商品图片列表
     * 约定顺序: [外观图(main), 细节图(detail), 二维码图(qrcode)]
     */
    private List<String> images;
}
