package com.fireworks.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fireworks.entity.InquiryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 询价商品关联 Mapper 接口
 */
@Mapper
public interface InquiryItemMapper extends BaseMapper<InquiryItem> {

    /**
     * 根据询价ID查询关联的商品列表（包含商品详情）
     */
    @Select("SELECT ii.*, p.name as product_name, p.price as product_price, p.category as product_category, " +
            "p.images as product_images FROM inquiry_item ii " +
            "LEFT JOIN product p ON ii.product_id = p.id " +
            "WHERE ii.inquiry_id = #{inquiryId}")
    List<InquiryItem> selectItemsWithProduct(@Param("inquiryId") Long inquiryId);
}
