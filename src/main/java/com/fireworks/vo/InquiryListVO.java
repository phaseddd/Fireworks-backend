package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 询价列表项 VO（管理端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryListVO {
    private Long id;
    private String phone;
    private String wechat;
    private Integer productCount;
    private String agentCode;
    private String agentName;
    private LocalDateTime createdAt;
}

