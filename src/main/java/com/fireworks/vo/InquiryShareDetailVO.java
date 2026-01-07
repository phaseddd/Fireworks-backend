package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分享详情页 VO（受控访问 + 联系方式脱敏）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryShareDetailVO {
    private String shareCode;
    private String agentCode;
    private String agentName;
    private String phoneMasked;
    private String wechatMasked;
    private List<InquiryItemVO> items;
    private LocalDateTime createdAt;
}

