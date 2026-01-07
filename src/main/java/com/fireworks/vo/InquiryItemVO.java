package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 询价商品项 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryItemVO {
    private Long productId;
    private String productName;
    private String image;
    private BigDecimal price;
    private Integer quantity;
}

