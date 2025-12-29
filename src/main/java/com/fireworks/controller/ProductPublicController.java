package com.fireworks.controller;

import com.fireworks.common.Result;
import com.fireworks.service.ProductService;
import com.fireworks.vo.PageVO;
import com.fireworks.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品公开接口（客户端）
 *
 * <p>仅返回上架商品，供客户端浏览使用</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/products/public")
@RequiredArgsConstructor
public class ProductPublicController {

    private final ProductService productService;

    /**
     * 获取商品列表（公开）
     *
     * @param page 页码（从1开始，默认1）
     * @param size 每页数量（默认20）
     * @return 分页商品列表
     */
    @GetMapping
    public Result<PageVO<ProductVO>> getPublicProductList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "20") Integer size) {
        log.debug("获取公开商品列表: sort={}, page={}, size={}", sort, page, size);
        return Result.success(productService.getPublicProductList(page, size, sort));
    }

    /**
     * 获取商品详情（公开）
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public Result<ProductVO> getPublicProductById(@PathVariable Long id) {
        log.debug("获取公开商品详情: id={}", id);
        return Result.success(productService.getPublicProductById(id));
    }
}
