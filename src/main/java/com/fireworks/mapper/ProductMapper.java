package com.fireworks.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fireworks.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper 接口
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 根据分类ID批量更新商品的 category 字段（分类名称）
     * <p>
     * 当分类名称变更时，需要同步更新所有关联商品的 category 字段，
     * 以保持数据一致性。
     *
     * @param categoryId   分类ID
     * @param categoryName 新的分类名称
     * @return 更新的记录数
     */
    @Update("UPDATE product SET category = #{categoryName} WHERE category_id = #{categoryId} AND deleted = 0")
    int updateCategoryNameByCategoryId(@Param("categoryId") Long categoryId, @Param("categoryName") String categoryName);
}
