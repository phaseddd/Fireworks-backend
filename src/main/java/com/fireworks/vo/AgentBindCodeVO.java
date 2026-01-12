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
    /**
     * 绑定二维码（扫码直达绑定页，可为空：生成失败时仅返回 bindCode）
     */
    private String bindQrcodeUrl;
    private LocalDateTime expiresAt;
}
