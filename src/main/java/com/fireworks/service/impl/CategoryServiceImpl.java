package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fireworks.exception.BusinessException;
import com.fireworks.dto.CreateCategoryRequest;
import com.fireworks.dto.UpdateCategoryRequest;
import com.fireworks.entity.Category;
import com.fireworks.mapper.CategoryMapper;
import com.fireworks.service.CategoryService;
import com.fireworks.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryVO> getAllCategories() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getId);
        List<Category> categories = categoryMapper.selectList(queryWrapper);
        return categories.stream()
                .map(CategoryVO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryVO> getActiveCategories() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getStatus, "ACTIVE")
                .orderByAsc(Category::getId);
        List<Category> categories = categoryMapper.selectList(queryWrapper);
        return categories.stream()
                .map(CategoryVO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryVO getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        return CategoryVO.fromEntity(category);
    }

    @Override
    @Transactional
    public CategoryVO createCategory(CreateCategoryRequest request) {
        String name = request.getName() != null ? request.getName().trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException(400, "分类名称不能为空");
        }

        // Check if name already exists
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getName, name);
        if (categoryMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException(400, "分类名称已存在");
        }

        Category category = new Category();
        category.setName(name);
        category.setStatus("ACTIVE");
        categoryMapper.insert(category);

        log.info("Created category: id={}, name={}", category.getId(), category.getName());
        return CategoryVO.fromEntity(category);
    }

    @Override
    @Transactional
    public CategoryVO updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }

        String name = request.getName() != null ? request.getName().trim() : "";
        if (name.isEmpty()) {
            throw new BusinessException(400, "分类名称不能为空");
        }

        // Check if new name already exists (exclude current category)
        if (!name.equals(category.getName())) {
            LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Category::getName, name)
                    .ne(Category::getId, id);
            if (categoryMapper.selectCount(queryWrapper) > 0) {
                throw new BusinessException(400, "分类名称已存在");
            }
            category.setName(name);
        }

        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }

        categoryMapper.updateById(category);

        log.info("Updated category: id={}, name={}", category.getId(), category.getName());
        return CategoryVO.fromEntity(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }

        // Check if there are products associated with this category
        int productCount = categoryMapper.countProductsByCategoryId(id);
        if (productCount > 0) {
            throw new BusinessException(400, "该分类下有 " + productCount + " 个商品，请先移除关联商品");
        }

        categoryMapper.deleteById(id);
        log.info("Deleted category: id={}, name={}", id, category.getName());
    }

    @Override
    public int countProductsByCategoryId(Long categoryId) {
        return categoryMapper.countProductsByCategoryId(categoryId);
    }
}
