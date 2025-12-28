package com.fireworks.service;

import com.fireworks.vo.PageVO;
import com.fireworks.vo.ProductVO;

/**
 * 商品服务接口
 */
public interface ProductService {

    /**
     * 获取商品列表（管理端，返回所有状态）
     *
     * @param status 商品状态筛选（可选）：ON_SHELF-上架, OFF_SHELF-下架
     * @param page   页码（从1开始）
     * @param size   每页数量
     * @return 分页商品列表
     */
    PageVO<ProductVO> getProductList(String status, Integer page, Integer size);

    /**
     * 获取商品详情
     *
     * @param id 商品ID
     * @return 商品详情
     */
    ProductVO getProductById(Long id);

    /**
     * 删除商品（逻辑删除）
     *
     * @param id 商品ID
     */
    void deleteProduct(Long id);
}
