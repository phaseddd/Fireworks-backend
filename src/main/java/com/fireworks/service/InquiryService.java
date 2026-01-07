package com.fireworks.service;

import com.fireworks.dto.CreateInquiryRequest;
import com.fireworks.vo.InquiryCreateVO;
import com.fireworks.vo.InquiryDetailVO;
import com.fireworks.vo.InquiryListVO;
import com.fireworks.vo.InquiryShareDetailVO;
import com.fireworks.vo.PageVO;

/**
 * 询价服务
 */
public interface InquiryService {

    InquiryCreateVO create(CreateInquiryRequest request, String openid);

    PageVO<InquiryListVO> list(Integer page, Integer size, String agentCode);

    InquiryDetailVO detail(Long id);

    InquiryShareDetailVO shareDetail(String shareCode, boolean admin, String openid);
}

