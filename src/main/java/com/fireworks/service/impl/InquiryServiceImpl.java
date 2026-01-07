package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fireworks.dto.CreateInquiryRequest;
import com.fireworks.entity.Agent;
import com.fireworks.entity.Inquiry;
import com.fireworks.entity.InquiryItem;
import com.fireworks.entity.Product;
import com.fireworks.exception.BusinessException;
import com.fireworks.mapper.AgentMapper;
import com.fireworks.mapper.InquiryItemMapper;
import com.fireworks.mapper.InquiryMapper;
import com.fireworks.mapper.ProductMapper;
import com.fireworks.service.InquiryService;
import com.fireworks.util.MaskUtil;
import com.fireworks.util.RandomCodeUtil;
import com.fireworks.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 询价服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryServiceImpl implements InquiryService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final InquiryMapper inquiryMapper;
    private final InquiryItemMapper inquiryItemMapper;
    private final ProductMapper productMapper;
    private final AgentMapper agentMapper;

    @Override
    @Transactional
    public InquiryCreateVO create(CreateInquiryRequest request, String openid) {
        // 校验商品是否存在（避免无效商品ID）
        List<Long> productIds = request.getItems().stream()
                .map(CreateInquiryRequest.Item::getProductId)
                .distinct()
                .toList();
        List<Product> products = productMapper.selectBatchIds(productIds);
        if (products.size() != productIds.size()) {
            throw new BusinessException(400, "包含无效商品，请刷新后重试");
        }

        String agentCode = StringUtils.hasText(request.getAgentCode()) ? request.getAgentCode().trim() : null;
        String validAgentCode = validateAgentCodeOrNull(agentCode);

        Inquiry inquiry = new Inquiry();
        inquiry.setAgentCode(validAgentCode);
        inquiry.setPhone(request.getPhone().trim());
        inquiry.setWechat(StringUtils.hasText(request.getWechat()) ? request.getWechat().trim() : null);
        inquiry.setOpenid(StringUtils.hasText(openid) ? openid : null);

        // shareCode 生成（少量重试避免 UNIQUE 冲突）
        String shareCode = null;
        for (int i = 0; i < 5; i++) {
            String candidate = RandomCodeUtil.generateShareCode();
            inquiry.setShareCode(candidate);
            try {
                int inserted = inquiryMapper.insert(inquiry);
                if (inserted <= 0) {
                    throw new BusinessException(500, "创建询价失败");
                }
                shareCode = candidate;
                break;
            } catch (Exception e) {
                log.warn("shareCode 冲突，重试: {}", e.getMessage());
            }
        }
        if (!StringUtils.hasText(shareCode)) {
            throw new BusinessException(500, "创建询价失败，请重试");
        }

        // 插入询价商品
        for (CreateInquiryRequest.Item item : request.getItems()) {
            InquiryItem ii = new InquiryItem();
            ii.setInquiryId(inquiry.getId());
            ii.setProductId(item.getProductId());
            ii.setQuantity(item.getQuantity());
            inquiryItemMapper.insert(ii);
        }

        return InquiryCreateVO.builder()
                .id(inquiry.getId())
                .shareCode(shareCode)
                .sharePath("/pages/inquiry/detail/index?shareCode=" + shareCode)
                .build();
    }

    @Override
    public PageVO<InquiryListVO> list(Integer page, Integer size, String agentCode) {
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 ? size : 20;

        LambdaQueryWrapper<Inquiry> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(agentCode)) {
            query.eq(Inquiry::getAgentCode, agentCode.trim());
        }
        query.orderByDesc(Inquiry::getCreatedAt);

        IPage<Inquiry> result = inquiryMapper.selectPage(new Page<>(pageNum, pageSize), query);
        List<Inquiry> records = result.getRecords();
        if (records.isEmpty()) {
            return PageVO.of(List.of(), result.getTotal(), pageNum, pageSize);
        }

        List<Long> inquiryIds = records.stream().map(Inquiry::getId).toList();
        Map<Long, Integer> productCountMap = loadProductCountMap(inquiryIds);
        Map<String, String> agentNameMap = loadAgentNameMap(records);

        List<InquiryListVO> items = records.stream().map(i -> InquiryListVO.builder()
                .id(i.getId())
                .phone(MaskUtil.maskPhone(i.getPhone()))
                .wechat(i.getWechat())
                .productCount(productCountMap.getOrDefault(i.getId(), 0))
                .agentCode(i.getAgentCode())
                // agentCode 可能为 null（游客直接询价），需要先检查再取值
                .agentName(i.getAgentCode() != null ? agentNameMap.get(i.getAgentCode()) : null)
                .createdAt(i.getCreatedAt())
                .build()).toList();

        return PageVO.of(items, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public InquiryDetailVO detail(Long id) {
        if (id == null) {
            throw new BusinessException(400, "询价ID不能为空");
        }
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BusinessException(404, "询价不存在");
        }

        List<InquiryItem> items = inquiryItemMapper.selectList(new LambdaQueryWrapper<InquiryItem>()
                .eq(InquiryItem::getInquiryId, id));
        List<Long> productIds = items.stream().map(InquiryItem::getProductId).distinct().toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<InquiryItemVO> itemVOs = items.stream().map(ii -> {
            Product product = productMap.get(ii.getProductId());
            return InquiryItemVO.builder()
                    .productId(ii.getProductId())
                    .productName(product != null ? product.getName() : null)
                    .price(product != null ? product.getPrice() : null)
                    .image(product != null && product.getImages() != null && !product.getImages().isEmpty()
                            ? product.getImages().get(0) : null)
                    .quantity(ii.getQuantity())
                    .build();
        }).toList();

        String agentName = null;
        if (StringUtils.hasText(inquiry.getAgentCode())) {
            Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                    .eq(Agent::getCode, inquiry.getAgentCode())
                    .last("LIMIT 1"));
            agentName = agent != null ? agent.getName() : null;
        }

        return InquiryDetailVO.builder()
                .id(inquiry.getId())
                .agentCode(inquiry.getAgentCode())
                .agentName(agentName)
                .phone(inquiry.getPhone())
                .wechat(inquiry.getWechat())
                .items(itemVOs)
                .createdAt(inquiry.getCreatedAt())
                .build();
    }

    @Override
    public InquiryShareDetailVO shareDetail(String shareCode, boolean admin, String openid) {
        if (!StringUtils.hasText(shareCode)) {
            throw new BusinessException(400, "shareCode 不能为空");
        }
        Inquiry inquiry = inquiryMapper.selectOne(new LambdaQueryWrapper<Inquiry>()
                .eq(Inquiry::getShareCode, shareCode.trim())
                .last("LIMIT 1"));
        if (inquiry == null) {
            throw new BusinessException(404, "询价不存在");
        }

        // 权限：管理员 JWT 或 代理商 OpenID（需绑定）
        if (!admin) {
            if (!StringUtils.hasText(openid)) {
                throw new BusinessException(403, "无权限查看该询价单");
            }

            Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                    .eq(Agent::getOpenid, openid)
                    .eq(Agent::getStatus, STATUS_ACTIVE)
                    .last("LIMIT 1"));
            if (agent == null) {
                throw new BusinessException(403, "无权限查看该询价单");
            }
            if (!StringUtils.hasText(inquiry.getAgentCode()) || !inquiry.getAgentCode().equals(agent.getCode())) {
                throw new BusinessException(403, "无权限查看该询价单");
            }
        }

        List<InquiryItem> items = inquiryItemMapper.selectList(new LambdaQueryWrapper<InquiryItem>()
                .eq(InquiryItem::getInquiryId, inquiry.getId()));
        List<Long> productIds = items.stream().map(InquiryItem::getProductId).distinct().toList();
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<InquiryItemVO> itemVOs = items.stream().map(ii -> {
            Product product = productMap.get(ii.getProductId());
            return InquiryItemVO.builder()
                    .productId(ii.getProductId())
                    .productName(product != null ? product.getName() : null)
                    .quantity(ii.getQuantity())
                    .build();
        }).toList();

        String agentName = null;
        if (StringUtils.hasText(inquiry.getAgentCode())) {
            Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                    .eq(Agent::getCode, inquiry.getAgentCode())
                    .last("LIMIT 1"));
            agentName = agent != null ? agent.getName() : null;
        }

        return InquiryShareDetailVO.builder()
                .shareCode(inquiry.getShareCode())
                .agentCode(inquiry.getAgentCode())
                .agentName(agentName)
                .phoneMasked(MaskUtil.maskPhone(inquiry.getPhone()))
                .wechatMasked(MaskUtil.maskWechat(inquiry.getWechat()))
                .items(itemVOs)
                .createdAt(inquiry.getCreatedAt())
                .build();
    }

    private String validateAgentCodeOrNull(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            return null;
        }
        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getCode, agentCode)
                .eq(Agent::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (agent == null) {
            log.warn("无效的代理商编码: {}", agentCode);
            return null;
        }
        return agent.getCode();
    }

    private Map<Long, Integer> loadProductCountMap(List<Long> inquiryIds) {
        List<InquiryItem> items = inquiryItemMapper.selectList(new LambdaQueryWrapper<InquiryItem>()
                .in(InquiryItem::getInquiryId, inquiryIds));
        Map<Long, Integer> countMap = new HashMap<>();
        for (InquiryItem item : items) {
            countMap.merge(item.getInquiryId(), item.getQuantity() != null ? item.getQuantity() : 0, Integer::sum);
        }
        return countMap;
    }

    private Map<String, String> loadAgentNameMap(List<Inquiry> inquiries) {
        List<String> agentCodes = inquiries.stream()
                .map(Inquiry::getAgentCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (agentCodes.isEmpty()) {
            return Map.of();
        }
        List<Agent> agents = agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                .in(Agent::getCode, agentCodes));
        return agents.stream().collect(Collectors.toMap(Agent::getCode, Agent::getName, (a, b) -> a));
    }
}

