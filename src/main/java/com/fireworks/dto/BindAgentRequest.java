package com.fireworks.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 代理商绑定请求
 */
@Data
public class BindAgentRequest {

    /**
     * 一次性绑定码
     */
    @NotBlank(message = "绑定码不能为空")
    private String bindCode;
}

