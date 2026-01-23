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

import java.util.List;

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
     * @param page       页码（从1开始，默认1）
     * @param size       每页数量（默认20）
     * @param sort       排序字段与方向（可选）
     * @param categoryId 分类ID筛选（可选，推荐使用）
     * @param category   商品分类筛选（可选，已废弃，保留兼容）：GIFT/FIREWORK/FIRECRACKER/COMBO/OTHER
     * @param minPrice   最低价格筛选（可选）
     * @param maxPrice   最高价格筛选（可选）
     * @param keyword    搜索关键词（可选）：模糊匹配商品名称
     * @return 分页商品列表
     */
    @GetMapping
    public Result<PageVO<ProductVO>> getPublicProductList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) String keyword) {
        log.debug("获取公开商品列表: categoryId={}, category={}, minPrice={}, maxPrice={}, keyword={}, sort={}, page={}, size={}",
                categoryId, category, minPrice, maxPrice, keyword, sort, page, size);
        return Result.success(productService.getPublicProductList(page, size, sort, categoryId, category, minPrice, maxPrice, keyword));
    }

    /**
     * 获取热门搜索关键词
     *
     * @return 热门关键词列表
     */
    @GetMapping("/hot-keywords")
    public Result<List<String>> getHotKeywords() {
        log.debug("获取热门搜索关键词");
        return Result.success(productService.getHotKeywords());
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
