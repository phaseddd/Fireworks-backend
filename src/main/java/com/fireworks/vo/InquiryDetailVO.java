package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 询价详情 VO（管理端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryDetailVO {
    private Long id;
    private String agentCode;
    private String agentName;
    private String phone;
    private String wechat;
    private List<InquiryItemVO> items;
    private LocalDateTime createdAt;
}

