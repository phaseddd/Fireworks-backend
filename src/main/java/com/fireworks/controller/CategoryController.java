package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.dto.CreateCategoryRequest;
import com.fireworks.dto.UpdateCategoryRequest;
import com.fireworks.service.CategoryService;
import com.fireworks.vo.CategoryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 获取所有分类（管理端，需要认证）
     *
     * @return 分类列表
     */
    @GetMapping
    public Result<List<CategoryVO>> getAllCategories() {
        log.debug("获取所有分类");
        List<CategoryVO> categories = categoryService.getAllCategories();
        return Result.success(categories);
    }

    /**
     * 获取启用状态的分类（客户端公开接口，无需认证）
     *
     * @return 启用状态的分类列表
     */
    @GetMapping("/active")
    public Result<List<CategoryVO>> getActiveCategories() {
        log.debug("获取启用状态的分类");
        List<CategoryVO> categories = categoryService.getActiveCategories();
        return Result.success(categories);
    }

    /**
     * 获取分类详情
     *
     * @param id 分类ID
     * @return 分类详情
     */
    @GetMapping("/{id}")
    public Result<CategoryVO> getCategoryById(@PathVariable Long id) {
        log.debug("获取分类详情: id={}", id);
        CategoryVO category = categoryService.getCategoryById(id);
        return Result.success(category);
    }

    /**
     * 创建分类（需要认证）
     *
     * @param request 创建分类请求
     * @return 创建的分类信息
     */
    @PostMapping
    public Result<CategoryVO> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        log.info("创建分类: name={}", request.getName());
        CategoryVO category = categoryService.createCategory(request);
        return Result.success("创建成功", category);
    }

    /**
     * 更新分类（需要认证）
     *
     * @param id      分类ID
     * @param request 更新分类请求
     * @return 更新后的分类信息
     */
    @PutMapping("/{id}")
    public Result<CategoryVO> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        log.info("更新分类: id={}", id);
        CategoryVO category = categoryService.updateCategory(id, request);
        return Result.success("更新成功", category);
    }

    /**
     * 删除分类（需要认证，有关联商品时禁止删除）
     *
     * @param id 分类ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        log.info("删除分类: id={}", id);
        categoryService.deleteCategory(id);
        return Result.success("删除成功", null);
    }

    /**
     * 获取分类下的商品数量
     *
     * @param id 分类ID
     * @return 商品数量
     */
    @GetMapping("/{id}/product-count")
    public Result<Integer> getProductCount(@PathVariable Long id) {
        log.debug("获取分类下商品数量: id={}", id);
        int count = categoryService.countProductsByCategoryId(id);
        return Result.success(count);
    }
}
