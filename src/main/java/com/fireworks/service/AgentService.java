package com.fireworks.service;

import com.fireworks.dto.BindAgentRequest;
import com.fireworks.dto.CreateAgentRequest;
import com.fireworks.dto.UpdateAgentRequest;
import com.fireworks.vo.*;

/**
 * 代理商服务
 */
public interface AgentService {

    PageVO<AgentVO> list(Integer page, Integer size);

    AgentVO detail(String code);

    AgentVO create(CreateAgentRequest request);

    AgentVO update(String code, UpdateAgentRequest request);

    String generateQrcode(String code);

    AgentBindCodeVO generateBindCode(String code);

    AgentBindResultVO bind(String openid, BindAgentRequest request);

    void unbind(String code);

    AgentStatsVO getStats(String code, String range);
}

