package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.dto.BindAgentRequest;
import com.fireworks.dto.CreateAgentRequest;
import com.fireworks.dto.UpdateAgentRequest;
import com.fireworks.service.AgentService;
import com.fireworks.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 代理商控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * 获取代理商列表（管理端）
     */
    @GetMapping
    public Result<PageVO<AgentVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        PageVO<AgentVO> result = agentService.list(page, size);
        return Result.success(result);
    }

    /**
     * 获取代理商详情（管理端）
     */
    @GetMapping("/{code}")
    public Result<AgentVO> detail(@PathVariable String code) {
        return Result.success(agentService.detail(code));
    }

    /**
     * 创建代理商（管理端）
     */
    @PostMapping
    public Result<AgentVO> create(@Valid @RequestBody CreateAgentRequest request) {
        log.info("创建代理商: name={}", request.getName());
        return Result.success("创建成功", agentService.create(request));
    }

    /**
     * 更新代理商（管理端）
     */
    @PutMapping("/{code}")
    public Result<AgentVO> update(
            @PathVariable String code,
            @RequestBody UpdateAgentRequest request
    ) {
        log.info("更新代理商: code={}", code);
        return Result.success("更新成功", agentService.update(code, request));
    }

    /**
     * 生成代理商专属小程序码（管理端）
     */
    @PostMapping("/{code}/qrcode")
    public Result<Map<String, String>> generateQrcode(@PathVariable String code) {
        log.info("生成代理商小程序码: code={}", code);
        String url = agentService.generateQrcode(code);
        return Result.success(Map.of("qrcodeUrl", url));
    }

    /**
     * 生成一次性绑定码（管理端）
     */
    @PostMapping("/{code}/bind-code")
    public Result<AgentBindCodeVO> generateBindCode(@PathVariable String code) {
        log.info("生成代理商绑定码: code={}", code);
        return Result.success(agentService.generateBindCode(code));
    }

    /**
     * 代理商绑定微信账号（小程序端，使用 OpenID）
     */
    @PostMapping("/bind")
    public Result<AgentBindResultVO> bind(
            HttpServletRequest request,
            @Valid @RequestBody BindAgentRequest body
    ) {
        String openid = getOpenIdFromHeader(request);
        return Result.success(agentService.bind(openid, body));
    }

    /**
     * 解除代理商绑定（管理端）
     */
    @PutMapping("/{code}/unbind")
    public Result<Void> unbind(@PathVariable String code) {
        log.info("解绑代理商: code={}", code);
        agentService.unbind(code);
        return Result.success("success", null);
    }

    /**
     * 获取业绩统计（管理端）
     */
    @GetMapping("/{code}/stats")
    public Result<AgentStatsVO> stats(
            @PathVariable String code,
            @RequestParam(required = false, defaultValue = "week") String range
    ) {
        return Result.success(agentService.getStats(code, range));
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

