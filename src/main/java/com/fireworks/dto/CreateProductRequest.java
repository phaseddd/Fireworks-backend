package com.fireworks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

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
     * 商品分类: GIFT-礼花类, FIREWORK-烟花类, FIRECRACKER-鞭炮类, COMBO-组合类, OTHER-其他
     */
    @Pattern(
            regexp = "^(GIFT|FIREWORK|FIRECRACKER|COMBO|OTHER)$",
            message = "商品分类不合法"
    )
    private String category = "OTHER";

    /**
     * 库存数量（默认0）
     */
    @PositiveOrZero(message = "库存数量不能为负数")
    private Integer stock = 0;

    /**
     * 商品描述（选填）
     */
    private String description;
}
