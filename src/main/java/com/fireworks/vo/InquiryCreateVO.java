package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建询价响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryCreateVO {
    private Long id;
    private String shareCode;
    private String sharePath;
}

