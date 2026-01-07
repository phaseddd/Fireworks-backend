package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理商绑定结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentBindResultVO {
    private String agentCode;
    private String agentName;
}

