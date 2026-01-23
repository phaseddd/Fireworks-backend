package com.fireworks.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fireworks.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商品 Mapper 接口
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 根据ID查询商品（带分类名称）
     *
     * @param id 商品ID
     * @return 商品信息（含分类名称）
     */
    @Select("""
        SELECT p.*, c.name AS category_name
        FROM product p
        LEFT JOIN category c ON p.category_id = c.id
        WHERE p.id = #{id} AND p.deleted = 0
        """)
    Product selectByIdWithCategory(@Param("id") Long id);
}
