package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fireworks.dto.CreateProductRequest;
import com.fireworks.dto.UpdateProductRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private static final String PUBLIC_PRODUCT_STATUS = "ON_SHELF";
    private static final String DEFAULT_STATUS = "ON_SHELF";

    @Override
    @Transactional
    public ProductVO createProduct(CreateProductRequest request) {
        // Build product entity
        Product product = new Product();
        product.setName(request.getName().trim());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory() != null ? request.getCategory() : "OTHER");
        product.setStock(request.getStock() != null ? request.getStock() : 0);
        product.setDescription(request.getDescription() != null ? request.getDescription().trim() : "");
        product.setStatus(DEFAULT_STATUS);

        // Images: [main, detail, qrcode]
        List<String> images = request.getImages() != null ? request.getImages() : new ArrayList<>();
        if (images.size() < 3) {
            throw new BusinessException(400, "商品图片参数不完整");
        }
        String mainImage = images.get(0);
        String qrcodeImage = images.get(2);
        if (!StringUtils.hasText(mainImage)) {
            throw new BusinessException(400, "请上传商品外观图");
        }
        if (!StringUtils.hasText(qrcodeImage)) {
            throw new BusinessException(400, "请上传燃放效果二维码图");
        }
        product.setImages(new ArrayList<>(images));

        // Insert to database
        int result = productMapper.insert(product);
        if (result <= 0) {
            throw new BusinessException(500, "创建商品失败");
        }

        log.info("商品创建成功: id={}, name={}", product.getId(), product.getName());
        return ProductVO.fromEntity(product);
    }

    @Override
    @Transactional
    public ProductVO updateProduct(Long id, UpdateProductRequest request) {
        if (id == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        // Check if product exists
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        // Validate images: [main, detail, qrcode]
        List<String> images = request.getImages();
        if (images == null || images.size() != 3) {
            throw new BusinessException(400, "商品图片参数不完整");
        }
        String mainImage = images.get(0);
        String qrcodeImage = images.get(2);
        if (!StringUtils.hasText(mainImage)) {
            throw new BusinessException(400, "请上传商品外观图");
        }
        if (!StringUtils.hasText(qrcodeImage)) {
            throw new BusinessException(400, "请上传燃放效果二维码图");
        }

        // Update product fields
        product.setName(request.getName().trim());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory() != null ? request.getCategory() : product.getCategory());
        product.setStock(request.getStock() != null ? request.getStock() : product.getStock());
        product.setDescription(request.getDescription() != null ? request.getDescription().trim() : product.getDescription());
        product.setStatus(request.getStatus() != null ? request.getStatus() : product.getStatus());
        product.setImages(new ArrayList<>(images));

        // Update to database
        int result = productMapper.updateById(product);
        if (result <= 0) {
            throw new BusinessException(500, "更新商品失败");
        }

        log.info("商品更新成功: id={}, name={}", product.getId(), product.getName());
        return ProductVO.fromEntity(product);
    }

    @Override
    public PageVO<ProductVO> getProductList(String status, String sort, Integer page, Integer size) {
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

        // 排序（默认按创建时间倒序）
        applySort(queryWrapper, sort);

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
    public PageVO<ProductVO> getPublicProductList(Integer page, Integer size, String sort, String category, Integer minPrice, Integer maxPrice) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }

        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Product::getStatus, PUBLIC_PRODUCT_STATUS);

        // 分类筛选
        if (StringUtils.hasText(category)) {
            queryWrapper.eq(Product::getCategory, category);
        }

        // 价格区间筛选
        if (minPrice != null) {
            queryWrapper.ge(Product::getPrice, minPrice);
        }
        if (maxPrice != null) {
            queryWrapper.le(Product::getPrice, maxPrice);
        }

        applySort(queryWrapper, sort);

        IPage<Product> productPage = productMapper.selectPage(new Page<>(page, size), queryWrapper);

        List<ProductVO> productVOList = productPage.getRecords().stream()
                .map(ProductVO::fromEntity)
                .collect(Collectors.toList());

        log.debug("查询公开商品列表: category={}, minPrice={}, maxPrice={}, page={}, size={}, total={}",
                category, minPrice, maxPrice, page, size, productPage.getTotal());
        return PageVO.of(productVOList, productPage.getTotal(), page, size);
    }

    private static void applySort(LambdaQueryWrapper<Product> queryWrapper, String sort) {
        if (!StringUtils.hasText(sort)) {
            queryWrapper.orderByDesc(Product::getCreatedAt);
            return;
        }

        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        boolean asc = !"desc".equals(direction);

        switch (field) {
            case "updatedAt" -> queryWrapper.orderBy(true, asc, Product::getUpdatedAt);
            case "createdAt" -> queryWrapper.orderBy(true, asc, Product::getCreatedAt);
            case "price" -> queryWrapper.orderBy(true, asc, Product::getPrice);
            case "id" -> queryWrapper.orderBy(true, asc, Product::getId);
            default -> queryWrapper.orderByDesc(Product::getCreatedAt);
        }
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
    public ProductVO getPublicProductById(Long id) {
        if (id == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        Product product = productMapper.selectById(id);
        if (product == null || !PUBLIC_PRODUCT_STATUS.equals(product.getStatus())) {
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
