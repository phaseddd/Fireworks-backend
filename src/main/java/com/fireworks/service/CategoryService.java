package com.fireworks.service;

import com.fireworks.dto.CreateCategoryRequest;
import com.fireworks.dto.UpdateCategoryRequest;
import com.fireworks.vo.CategoryVO;

import java.util.List;

/**
 * 分类服务接口
 */
public interface CategoryService {

    /**
     * 获取所有分类（管理端，包含禁用状态）
     *
     * @return 分类列表
     */
    List<CategoryVO> getAllCategories();

    /**
     * 获取启用状态的分类（客户端公开接口）
     *
     * @return 启用状态的分类列表
     */
    List<CategoryVO> getActiveCategories();

    /**
     * 根据ID获取分类
     *
     * @param id 分类ID
     * @return 分类信息
     */
    CategoryVO getCategoryById(Long id);

    /**
     * 创建分类
     *
     * @param request 创建分类请求
     * @return 创建的分类信息
     */
    CategoryVO createCategory(CreateCategoryRequest request);

    /**
     * 更新分类
     *
     * @param id      分类ID
     * @param request 更新分类请求
     * @return 更新后的分类信息
     */
    CategoryVO updateCategory(Long id, UpdateCategoryRequest request);

    /**
     * 删除分类
     *
     * @param id 分类ID
     */
    void deleteCategory(Long id);

    /**
     * 统计分类下的商品数量
     *
     * @param categoryId 分类ID
     * @return 商品数量
     */
    int countProductsByCategoryId(Long categoryId);
}
