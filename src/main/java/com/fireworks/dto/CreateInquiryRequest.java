package com.fireworks.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建询价请求
 */
@Data
public class CreateInquiryRequest {

    /**
     * 来源代理商编码（可选）
     */
    private String agentCode;

    /**
     * 客户手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 客户微信号（可选）
     */
    private String wechat;

    /**
     * 询价商品列表
     */
    @Valid
    @NotEmpty(message = "请选择至少 1 个商品")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "商品数量不能为空")
        @Min(value = 1, message = "商品数量最小为 1")
        private Integer quantity;
    }
}

