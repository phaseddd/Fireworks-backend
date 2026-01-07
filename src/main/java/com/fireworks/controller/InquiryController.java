package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.config.JwtAuthInterceptor;
import com.fireworks.dto.CreateInquiryRequest;
import com.fireworks.service.InquiryService;
import com.fireworks.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 询价控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    /**
     * 创建询价（客户端）
     */
    @PostMapping
    public Result<InquiryCreateVO> create(
            HttpServletRequest request,
            @Valid @RequestBody CreateInquiryRequest body
    ) {
        String openid = getOpenIdFromHeader(request);
        InquiryCreateVO result = inquiryService.create(body, openid);
        return Result.success("创建成功", result);
    }

    /**
     * 询价列表（管理端）
     */
    @GetMapping
    public Result<PageVO<InquiryListVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String agentCode
    ) {
        return Result.success(inquiryService.list(page, size, agentCode));
    }

    /**
     * 询价详情（管理端）
     */
    @GetMapping("/{id}")
    public Result<InquiryDetailVO> detail(@PathVariable Long id) {
        return Result.success(inquiryService.detail(id));
    }

    /**
     * 分享详情（受控访问：管理员 JWT / 代理商 OpenID）
     */
    @GetMapping("/share/{shareCode}")
    public Result<InquiryShareDetailVO> shareDetail(
            HttpServletRequest request,
            @PathVariable String shareCode
    ) {
        boolean admin = JwtAuthInterceptor.getCurrentUser() != null;
        String openid = getOpenIdFromHeader(request);
        return Result.success(inquiryService.shareDetail(shareCode, admin, openid));
    }

    private String getOpenIdFromHeader(HttpServletRequest request) {
        if (request == null) return null;
        String openid = request.getHeader("X-WX-OPENID");
        if (openid == null || openid.isBlank()) {
            openid = request.getHeader("x-wx-openid");
        }
        if (openid == null || openid.isBlank()) {
            openid = request.getHeader("X-OPENID");
        }
        return (openid == null || openid.isBlank()) ? null : openid.trim();
    }
}

