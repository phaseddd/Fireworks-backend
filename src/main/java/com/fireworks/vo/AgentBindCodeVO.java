package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 代理商绑定码响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentBindCodeVO {
    private String bindCode;
    private LocalDateTime expiresAt;
}

