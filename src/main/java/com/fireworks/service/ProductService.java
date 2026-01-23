package com.fireworks.service;

import com.fireworks.dto.CreateProductRequest;
import com.fireworks.dto.UpdateProductRequest;
import com.fireworks.vo.PageVO;
import com.fireworks.vo.ProductVO;

/**
 * 商品服务接口
 */
public interface ProductService {

    /**
     * 创建商品
     *
     * @param request 创建商品请求
     * @return 创建的商品信息
     */
    ProductVO createProduct(CreateProductRequest request);

    /**
     * 更新商品
     *
     * @param id      商品ID
     * @param request 更新商品请求
     * @return 更新后的商品信息
     */
    ProductVO updateProduct(Long id, UpdateProductRequest request);

    /**
     * 获取商品列表（管理端，返回所有状态）
     *
     * @param status 商品状态筛选（可选）：ON_SHELF-上架, OFF_SHELF-下架
     * @param sort   排序字段与方向（可选）：例如 updatedAt,desc
     * @param page   页码（从1开始）
     * @param size   每页数量
     * @return 分页商品列表
     */
    PageVO<ProductVO> getProductList(String status, String sort, Integer page, Integer size);

    /**
     * 获取商品列表（客户端公开接口，仅返回上架商品）
     *
     * @param page       页码（从1开始）
     * @param size       每页数量
     * @param sort       排序字段与方向（可选）：例如 updatedAt,desc
     * @param categoryId 分类ID筛选（可选，推荐使用）
     * @param category   商品分类筛选（可选，已废弃）：GIFT/FIREWORK/FIRECRACKER/COMBO/OTHER
     * @param minPrice   最低价格筛选（可选）
     * @param maxPrice   最高价格筛选（可选）
     * @param keyword    搜索关键词（可选）：模糊匹配商品名称
     * @return 分页商品列表
     */
    PageVO<ProductVO> getPublicProductList(Integer page, Integer size, String sort, Long categoryId, String category, Integer minPrice, Integer maxPrice, String keyword);

    /**
     * 获取热门搜索关键词
     *
     * @return 热门关键词列表
     */
    java.util.List<String> getHotKeywords();

    /**
     * 获取商品详情
     *
     * @param id 商品ID
     * @return 商品详情
     */
    ProductVO getProductById(Long id);

    /**
     * 获取商品详情（客户端公开接口，仅允许访问上架商品）
     *
     * @param id 商品ID
     * @return 商品详情
     */
    ProductVO getPublicProductById(Long id);

    /**
     * 删除商品（逻辑删除）
     *
     * @param id 商品ID
     */
    void deleteProduct(Long id);

    /**
     * 更新商品视频提取信息
     *
     * @param id        商品ID
     * @param videoUrl  视频URL（可为null）
     * @param status    视频提取状态（可为null）
     * @param message   视频提取说明/失败原因（可为null）
     * @param targetUrl 视频提取目标网址（可为null）
     * @return 更新后的商品信息
     */
    ProductVO updateVideoExtractInfo(Long id, String videoUrl, String status, String message, String targetUrl);
}
