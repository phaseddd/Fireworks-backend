package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fireworks.dto.BindAgentRequest;
import com.fireworks.dto.CreateAgentRequest;
import com.fireworks.dto.UpdateAgentRequest;
import com.fireworks.entity.Agent;
import com.fireworks.entity.Inquiry;
import com.fireworks.exception.BusinessException;
import com.fireworks.mapper.AgentMapper;
import com.fireworks.mapper.InquiryMapper;
import com.fireworks.service.AgentService;
import com.fireworks.service.FileStorageService;
import com.fireworks.service.WechatCloudService;
import com.fireworks.util.RandomCodeUtil;
import com.fireworks.vo.AgentBindCodeVO;
import com.fireworks.vo.AgentBindResultVO;
import com.fireworks.vo.AgentStatsVO;
import com.fireworks.vo.AgentVO;
import com.fireworks.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 代理商服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final AgentMapper agentMapper;
    private final InquiryMapper inquiryMapper;
    private final FileStorageService fileStorageService;
    private final WechatCloudService wechatCloudService;

    @Override
    public PageVO<AgentVO> list(Integer page, Integer size) {
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 ? size : 50;

        LambdaQueryWrapper<Agent> query = new LambdaQueryWrapper<>();
        query.orderByDesc(Agent::getCreatedAt);

        IPage<Agent> result = agentMapper.selectPage(new Page<>(pageNum, pageSize), query);
        List<AgentVO> items = result.getRecords().stream().map(AgentVO::fromEntity).toList();
        return PageVO.of(items, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public AgentVO detail(String code) {
        Agent agent = findByCode(code).orElseThrow(() -> new BusinessException(404, "代理商不存在"));
        return AgentVO.fromEntity(agent);
    }

    @Override
    @Transactional
    public AgentVO create(CreateAgentRequest request) {
        String name = request.getName() != null ? request.getName().trim() : "";
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(400, "代理商名称不能为空");
        }

        String phone = request.getPhone() != null ? request.getPhone().trim() : null;
        if (StringUtils.hasText(phone)) {
            ensurePhoneUnique(phone, null);
        }

        String code = generateNextCode();
        Agent agent = new Agent();
        agent.setCode(code);
        agent.setName(name);
        agent.setPhone(StringUtils.hasText(phone) ? phone : null);
        agent.setStatus(STATUS_ACTIVE);

        int inserted = agentMapper.insert(agent);
        if (inserted <= 0) {
            throw new BusinessException(500, "创建代理商失败");
        }
        return AgentVO.fromEntity(agent);
    }

    @Override
    @Transactional
    public AgentVO update(String code, UpdateAgentRequest request) {
        Agent agent = findByCode(code).orElseThrow(() -> new BusinessException(404, "代理商不存在"));

        String name = request.getName() != null ? request.getName().trim() : null;
        String phone = request.getPhone() != null ? request.getPhone().trim() : null;
        String status = request.getStatus() != null ? request.getStatus().trim().toUpperCase(Locale.ROOT) : null;

        if (StringUtils.hasText(phone)) {
            ensurePhoneUnique(phone, agent.getId());
        }

        if (StringUtils.hasText(status) && !STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(400, "状态仅支持 ACTIVE/DISABLED");
        }

        LambdaUpdateWrapper<Agent> update = new LambdaUpdateWrapper<>();
        update.eq(Agent::getId, agent.getId());
        boolean hasUpdate = false;

        if (StringUtils.hasText(name)) {
            update.set(Agent::getName, name);
            hasUpdate = true;
        }
        if (request.getPhone() != null) {
            update.set(Agent::getPhone, StringUtils.hasText(phone) ? phone : null);
            hasUpdate = true;
        }
        if (StringUtils.hasText(status)) {
            update.set(Agent::getStatus, status);
            hasUpdate = true;
        }

        if (hasUpdate) {
            int updated = agentMapper.update(null, update);
            if (updated <= 0) {
                throw new BusinessException(500, "更新失败");
            }
        }

        return AgentVO.fromEntity(findByCode(code).orElseThrow());
    }

    @Override
    @Transactional
    public String generateQrcode(String code) {
        Agent agent = findByCode(code).orElseThrow(() -> new BusinessException(404, "代理商不存在"));

        // 小程序码(scene)规范：a={agentCode}
        String scene = "a=" + agent.getCode();

        // 使用微信云调用生成小程序码（wxacode.getUnlimited）
        byte[] png = wechatCloudService.generateWxaCode(scene, "pages/index/index");
        String filename = "agent_" + agent.getCode() + ".png";
        String url = fileStorageService.save("qrcode/", filename, png);

        LambdaUpdateWrapper<Agent> update = new LambdaUpdateWrapper<>();
        update.eq(Agent::getId, agent.getId()).set(Agent::getQrcodeUrl, url);
        agentMapper.update(null, update);

        return url;
    }

    @Override
    @Transactional
    public AgentBindCodeVO generateBindCode(String code) {
        Agent agent = findByCode(code).orElseThrow(() -> new BusinessException(404, "代理商不存在"));

        // 30 分钟有效
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        // 少量重试避免 UNIQUE 冲突
        for (int i = 0; i < 5; i++) {
            String bindCode = RandomCodeUtil.generateBindCode();
            LambdaUpdateWrapper<Agent> update = new LambdaUpdateWrapper<>();
            update.eq(Agent::getId, agent.getId())
                    .set(Agent::getBindCode, bindCode)
                    .set(Agent::getBindCodeExpiresAt, expiresAt);
            try {
                agentMapper.update(null, update);
                String bindQrcodeUrl = null;
                try {
                    // 生成“绑定二维码”：扫码直达绑定页，scene 携带绑定码
                    String scene = "b=" + bindCode;
                    byte[] png = wechatCloudService.generateWxaCode(scene, "pages/agent/bind/index");
                    String filename = "agent_bind_" + agent.getCode() + ".png";
                    bindQrcodeUrl = fileStorageService.save("qrcode/", filename, png);
                } catch (Exception e) {
                    // 兜底：二维码生成失败不影响绑定码使用（仍可手动输入）
                    log.warn("生成绑定二维码失败，将仅返回绑定码: {}", e.getMessage());
                }

                return AgentBindCodeVO.builder()
                        .bindCode(bindCode)
                        .bindQrcodeUrl(bindQrcodeUrl)
                        .expiresAt(expiresAt)
                        .build();
            } catch (Exception e) {
                log.warn("生成绑定码冲突，重试: {}", e.getMessage());
            }
        }

        throw new BusinessException(500, "生成绑定码失败，请重试");
    }

    @Override
    @Transactional
    public AgentBindResultVO bind(String openid, BindAgentRequest request) {
        if (!StringUtils.hasText(openid)) {
            throw new BusinessException(400, "缺少 OpenID");
        }
        String bindCode = request.getBindCode() != null ? request.getBindCode().trim() : "";
        if (!StringUtils.hasText(bindCode)) {
            throw new BusinessException(400, "绑定码不能为空");
        }

        // OpenID 已绑定到其他代理商时阻止
        Agent existed = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getOpenid, openid)
                .last("LIMIT 1"));
        if (existed != null) {
            return AgentBindResultVO.builder()
                    .agentCode(existed.getCode())
                    .agentName(existed.getName())
                    .build();
        }

        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getBindCode, bindCode)
                .last("LIMIT 1"));
        if (agent == null) {
            throw new BusinessException(400, "绑定码无效");
        }
        if (agent.getBindCodeExpiresAt() != null && agent.getBindCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "绑定码已过期");
        }

        LambdaUpdateWrapper<Agent> update = new LambdaUpdateWrapper<>();
        update.eq(Agent::getId, agent.getId())
                .set(Agent::getOpenid, openid)
                .set(Agent::getBindCode, null)
                .set(Agent::getBindCodeExpiresAt, null);
        agentMapper.update(null, update);

        return AgentBindResultVO.builder()
                .agentCode(agent.getCode())
                .agentName(agent.getName())
                .build();
    }

    @Override
    @Transactional
    public void unbind(String code) {
        Agent agent = findByCode(code).orElseThrow(() -> new BusinessException(404, "代理商不存在"));
        LambdaUpdateWrapper<Agent> update = new LambdaUpdateWrapper<>();
        update.eq(Agent::getId, agent.getId())
                .set(Agent::getOpenid, null)
                .set(Agent::getBindCode, null)
                .set(Agent::getBindCodeExpiresAt, null);
        agentMapper.update(null, update);
    }

    @Override
    public AgentStatsVO getStats(String code, String range) {
        Agent agent = findByCode(code).orElseThrow(() -> new BusinessException(404, "代理商不存在"));

        String normalizedRange = normalizeRange(range);
        LocalDateTime startTime = switch (normalizedRange) {
            case "week" -> LocalDateTime.now().minusDays(7);
            case "month" -> LocalDateTime.now().minusMonths(1);
            default -> null;
        };

        LambdaQueryWrapper<Inquiry> query = new LambdaQueryWrapper<>();
        query.eq(Inquiry::getAgentCode, agent.getCode());
        if (startTime != null) {
            query.ge(Inquiry::getCreatedAt, startTime);
        }
        List<Inquiry> inquiries = inquiryMapper.selectList(query);

        int inquiryCount = inquiries.size();
        int customerCount = (int) inquiries.stream()
                .map(Inquiry::getOpenid)
                .filter(StringUtils::hasText)
                .distinct()
                .count();

        return AgentStatsVO.builder()
                .agentCode(agent.getCode())
                .agentName(agent.getName())
                .range(normalizedRange)
                .customerCount(customerCount)
                .inquiryCount(inquiryCount)
                .build();
    }

    private Optional<Agent> findByCode(String code) {
        if (!StringUtils.hasText(code)) return Optional.empty();
        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getCode, code.trim())
                .last("LIMIT 1"));
        return Optional.ofNullable(agent);
    }

    private void ensurePhoneUnique(String phone, Long selfId) {
        Agent existed = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getPhone, phone)
                .last("LIMIT 1"));
        if (existed != null && (selfId == null || !Objects.equals(existed.getId(), selfId))) {
            throw new BusinessException(400, "该手机号已存在");
        }
    }

    private String generateNextCode() {
        List<Agent> agents = agentMapper.selectList(new LambdaQueryWrapper<Agent>().select(Agent::getCode));
        int max = agents.stream()
                .map(Agent::getCode)
                .filter(StringUtils::hasText)
                .map(c -> c.substring(1))
                .filter(s -> s.chars().allMatch(Character::isDigit))
                .map(Integer::parseInt)
                .max(Comparator.naturalOrder())
                .orElse(0);
        int next = max + 1;
        if (next > 999) {
            throw new BusinessException(400, "代理商编码已用尽");
        }
        return String.format("A%03d", next);
    }

    private String normalizeRange(String range) {
        if (!StringUtils.hasText(range)) return "week";
        String v = range.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "week", "month", "all" -> v;
            default -> "week";
        };
    }
}
