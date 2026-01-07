package com.fireworks.dto;

import lombok.Data;

/**
 * 更新代理商请求（部分更新）
 */
@Data
public class UpdateAgentRequest {

    /**
     * 代理商名称（可选）
     */
    private String name;

    /**
     * 联系电话（可选）
     */
    private String phone;

    /**
     * 状态（可选）：ACTIVE / DISABLED
     */
    private String status;
}

