package com.fireworks.vo;

import com.fireworks.entity.Agent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 代理商响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentVO {

    private Long id;
    private String code;
    private String name;
    private String phone;
    private String status;
    private String qrcodeUrl;
    private Boolean openidBound;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AgentVO fromEntity(Agent agent) {
        if (agent == null) return null;
        return AgentVO.builder()
                .id(agent.getId())
                .code(agent.getCode())
                .name(agent.getName())
                .phone(agent.getPhone())
                .status(agent.getStatus())
                .qrcodeUrl(agent.getQrcodeUrl())
                .openidBound(agent.getOpenid() != null && !agent.getOpenid().isBlank())
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .build();
    }
}

