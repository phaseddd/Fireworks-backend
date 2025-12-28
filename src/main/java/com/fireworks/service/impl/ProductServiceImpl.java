package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fireworks.entity.Product;
import com.fireworks.exception.BusinessException;
import com.fireworks.mapper.ProductMapper;
import com.fireworks.service.ProductService;
import com.fireworks.vo.PageVO;
import com.fireworks.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    public PageVO<ProductVO> getProductList(String status, Integer page, Integer size) {
        // 参数校验和默认值
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }

        // 构建查询条件
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();

        // 状态筛选
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(Product::getStatus, status);
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc(Product::getCreatedAt);

        // 分页查询
        IPage<Product> productPage = productMapper.selectPage(
                new Page<>(page, size),
                queryWrapper
        );

        // 转换为 VO
        List<ProductVO> productVOList = productPage.getRecords().stream()
                .map(ProductVO::fromEntity)
                .collect(Collectors.toList());

        log.debug("查询商品列表: status={}, page={}, size={}, total={}",
                status, page, size, productPage.getTotal());

        return PageVO.of(productVOList, productPage.getTotal(), page, size);
    }

    @Override
    public ProductVO getProductById(Long id) {
        if (id == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        return ProductVO.fromEntity(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        if (id == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        // MyBatis-Plus 逻辑删除
        int result = productMapper.deleteById(id);
        if (result > 0) {
            log.info("商品删除成功: id={}, name={}", id, product.getName());
        } else {
            throw new BusinessException(500, "删除失败");
        }
    }
}
