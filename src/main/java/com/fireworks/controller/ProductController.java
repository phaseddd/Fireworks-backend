package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.service.ProductService;
import com.fireworks.vo.PageVO;
import com.fireworks.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理控制器（管理端）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 获取商品列表（管理端，需要认证）
     *
     * @param status 商品状态筛选（可选）：ON_SHELF-上架, OFF_SHELF-下架
     * @param page   页码（从1开始，默认1）
     * @param size   每页数量（默认20）
     * @return 分页商品列表
     */
    @GetMapping
    public Result<PageVO<ProductVO>> getProductList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.debug("获取商品列表: status={}, page={}, size={}", status, page, size);
        PageVO<ProductVO> pageVO = productService.getProductList(status, page, size);
        return Result.success(pageVO);
    }

    /**
     * 获取商品详情
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public Result<ProductVO> getProductById(@PathVariable Long id) {
        log.debug("获取商品详情: id={}", id);
        ProductVO productVO = productService.getProductById(id);
        return Result.success(productVO);
    }

    /**
     * 删除商品（逻辑删除）
     *
     * @param id 商品ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        log.info("删除商品: id={}", id);
        productService.deleteProduct(id);
        return Result.success("删除成功", null);
    }
}
