package com.fireworks.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建代理商请求
 */
@Data
public class CreateAgentRequest {

    /**
     * 代理商名称
     */
    @NotBlank(message = "代理商名称不能为空")
    private String name;

    /**
     * 联系电话（可选）
     */
    private String phone;
}

