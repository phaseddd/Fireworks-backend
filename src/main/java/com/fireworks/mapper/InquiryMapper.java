package com.fireworks.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fireworks.entity.Inquiry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 询价记录 Mapper 接口
 */
@Mapper
public interface InquiryMapper extends BaseMapper<Inquiry> {
}
