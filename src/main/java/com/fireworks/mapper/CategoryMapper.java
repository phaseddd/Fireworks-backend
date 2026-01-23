package com.fireworks.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fireworks.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分类数据访问层
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 统计分类下的商品数量（删除前校验用）
     *
     * @param categoryId 分类ID
     * @return 关联的商品数量
     */
    @Select("SELECT COUNT(*) FROM product WHERE category_id = #{categoryId} AND deleted = 0")
    int countProductsByCategoryId(@Param("categoryId") Long categoryId);
}
