package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理商业绩统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStatsVO {
    private String agentCode;
    private String agentName;
    private String range;
    private Integer customerCount;
    private Integer inquiryCount;
}

