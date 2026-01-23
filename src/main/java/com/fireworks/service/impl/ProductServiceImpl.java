package com.fireworks.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fireworks.dto.CreateProductRequest;
import com.fireworks.dto.UpdateProductRequest;
import com.fireworks.entity.Category;
import com.fireworks.entity.Product;
import com.fireworks.exception.BusinessException;
import com.fireworks.mapper.CategoryMapper;
import com.fireworks.mapper.ProductMapper;
import com.fireworks.service.ProductVideoExtractAsyncService;
import com.fireworks.service.ProductService;
import com.fireworks.vo.PageVO;
import com.fireworks.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 * <p>
 * 负责商品的CRUD操作，包括：
 * <ul>
 *   <li>商品创建、更新、删除</li>
 *   <li>商品列表查询（管理端/公开端）</li>
 *   <li>商品详情查询</li>
 *   <li>视频提取信息更新</li>
 * </ul>
 * <p>
 * 商品保存时会自动触发异步视频提取流程（不阻塞保存操作）
 *
 * @see ProductVideoExtractAsyncService 异步视频提取服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductVideoExtractAsyncService productVideoExtractAsyncService;

    /** 公开商品状态：上架 */
    private static final String PUBLIC_PRODUCT_STATUS = "ON_SHELF";
    /** 默认商品状态：上架 */
    private static final String DEFAULT_STATUS = "ON_SHELF";

    /**
     * 创建商品
     * <p>
     * 业务流程：
     * <ol>
     *   <li>校验图片参数（必须包含3张图：外观图、详情图、二维码图）</li>
     *   <li>构建商品实体并保存到数据库</li>
     *   <li>事务提交后，异步触发视频提取任务（从第3张二维码图中提取视频URL）</li>
     * </ol>
     *
     * @param request 创建商品请求，包含名称、价格、分类、库存、描述、图片列表
     * @return 创建成功的商品VO
     * @throws BusinessException 图片参数不完整或保存失败时抛出
     */
    @Override
    @Transactional
    public ProductVO createProduct(CreateProductRequest request) {
        // Build product entity
        Product product = new Product();
        product.setName(request.getName().trim());
        product.setPrice(request.getPrice());
        // 设置分类ID，并同步设置分类名称到 category 字段（兼容旧代码）
        if (request.getCategoryId() != null) {
            product.setCategoryId(request.getCategoryId());
            // 查询分类名称，同步到 category 字段
            Category cat = categoryMapper.selectById(request.getCategoryId());
            if (cat != null) {
                product.setCategory(cat.getName());
            }
        }
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

        // 事务提交后异步触发视频提取（resetVideoUrl=false：新建商品无需重置）
        runAfterCommit(() -> productVideoExtractAsyncService.extractAndUpdate(product.getId(), qrcodeImage, false));

        // 填充分类名称（便于前端直接展示）
        fillCategoryNames(List.of(product));
        return ProductVO.fromEntity(product);
    }

    /**
     * 更新商品
     * <p>
     * 业务流程：
     * <ol>
     *   <li>校验商品是否存在</li>
     *   <li>校验图片参数完整性</li>
     *   <li>检测第3张二维码图是否变更</li>
     *   <li>更新商品信息到数据库</li>
     *   <li>若二维码图变更，事务提交后异步重新触发视频提取</li>
     * </ol>
     *
     * @param id      商品ID
     * @param request 更新商品请求
     * @return 更新后的商品VO
     * @throws BusinessException 商品不存在、参数不完整或更新失败时抛出
     */
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

        boolean qrcodeChanged = product.getImages() == null
                || product.getImages().size() < 3
                || !java.util.Objects.equals(product.getImages().get(2), qrcodeImage);

        // Update product fields
        product.setName(request.getName().trim());
        product.setPrice(request.getPrice());
        // 设置分类ID，并同步设置分类名称到 category 字段（兼容旧代码）
        if (request.getCategoryId() != null) {
            product.setCategoryId(request.getCategoryId());
            // 查询分类名称，同步到 category 字段
            Category cat = categoryMapper.selectById(request.getCategoryId());
            if (cat != null) {
                product.setCategory(cat.getName());
            }
        }
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

        // 仅当二维码图变更时才重新触发视频提取（resetVideoUrl=true：更新时需重置旧视频URL）
        if (qrcodeChanged) {
            runAfterCommit(() -> productVideoExtractAsyncService.extractAndUpdate(product.getId(), qrcodeImage, true));
        }

        // 填充分类名称（便于前端直接展示）
        fillCategoryNames(List.of(product));
        return ProductVO.fromEntity(product);
    }

    /**
     * 在事务提交后执行任务
     * <p>
     * 用于确保异步任务在数据库事务成功提交后才执行，
     * 避免异步任务读取到未提交的数据或事务回滚后仍执行任务。
     *
     * @param task 要执行的任务
     */
    private static void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }

        task.run();
    }

    /**
     * 获取商品列表（管理端）
     * <p>
     * 支持按状态筛选和排序，返回分页结果。
     *
     * @param status 商品状态筛选（可选）
     * @param sort   排序方式，格式："字段名,asc/desc"（可选，默认按创建时间倒序）
     * @param page   页码（从1开始）
     * @param size   每页数量
     * @return 商品分页列表
     */
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

        // 转换为VO
        List<Product> products = productPage.getRecords();
        fillCategoryNames(products);

        List<ProductVO> productVOList = products.stream()
                .map(ProductVO::fromEntity)
                .collect(Collectors.toList());

        log.debug("查询商品列表: status={}, page={}, size={}, total={}",
                status, page, size, productPage.getTotal());

        return PageVO.of(productVOList, productPage.getTotal(), page, size);
    }

    /**
     * 获取公开商品列表（小程序端）
     * <p>
     * 仅返回状态为"上架"的商品，支持多维度筛选：
     * <ul>
     *   <li>分类ID筛选（推荐使用）</li>
     *   <li>分类筛选（已废弃，保留兼容）</li>
     *   <li>价格区间筛选</li>
     *   <li>关键词模糊搜索（按商品名称）</li>
     * </ul>
     *
     * @param page       页码（从1开始）
     * @param size       每页数量
     * @param sort       排序方式
     * @param categoryId 分类ID筛选（可选，推荐使用）
     * @param category   分类筛选（可选，已废弃）
     * @param minPrice   最低价格（可选）
     * @param maxPrice   最高价格（可选）
     * @param keyword    搜索关键词（可选）
     * @return 商品分页列表
     */
    @Override
    public PageVO<ProductVO> getPublicProductList(Integer page, Integer size, String sort, Long categoryId, String category, Integer minPrice, Integer maxPrice, String keyword) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 20;
        }

        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Product::getStatus, PUBLIC_PRODUCT_STATUS);

        // 分类ID筛选（优先使用 categoryId）
        if (categoryId != null) {
            queryWrapper.eq(Product::getCategoryId, categoryId);
        } else if (StringUtils.hasText(category)) {
            // 兼容旧的 category 字符串筛选
            queryWrapper.eq(Product::getCategory, category);
        }

        // 价格区间筛选
        if (minPrice != null) {
            queryWrapper.ge(Product::getPrice, minPrice);
        }
        if (maxPrice != null) {
            queryWrapper.le(Product::getPrice, maxPrice);
        }

        // 关键词模糊搜索
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(Product::getName, keyword);
        }

        applySort(queryWrapper, sort);

        IPage<Product> productPage = productMapper.selectPage(new Page<>(page, size), queryWrapper);

        List<Product> products = productPage.getRecords();
        fillCategoryNames(products);

        List<ProductVO> productVOList = products.stream()
                .map(ProductVO::fromEntity)
                .collect(Collectors.toList());

        log.debug("查询公开商品列表: categoryId={}, category={}, minPrice={}, maxPrice={}, keyword={}, page={}, size={}, total={}",
                categoryId, category, minPrice, maxPrice, keyword, page, size, productPage.getTotal());
        return PageVO.of(productVOList, productPage.getTotal(), page, size);
    }

    /**
     * 获取热门搜索关键词
     * <p>
     * 当前为静态配置，后续可扩展为基于搜索统计动态获取。
     *
     * @return 热门关键词列表
     */
    @Override
    public List<String> getHotKeywords() {
        // 静态配置热门关键词（后续可扩展为基于搜索统计动态获取）
        return java.util.Arrays.asList("烟花", "礼花", "鞭炮", "仙女棒", "孔明灯", "烟火", "礼品装");
    }

    /**
     * 应用排序条件到查询构建器
     * <p>
     * 支持的排序字段：updatedAt、createdAt、price、id
     * 默认按创建时间倒序排列。
     *
     * @param queryWrapper 查询构建器
     * @param sort         排序参数，格式："字段名,asc/desc"
     */
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

    /**
     * 批量填充分类名称（避免前端在兼容期只能显示旧的 category 枚举）
     *
     * @param products 商品列表（可为空）
     */
    private void fillCategoryNames(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        List<Long> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (categoryIds.isEmpty()) {
            return;
        }

        List<Category> categories = categoryMapper.selectBatchIds(categoryIds);
        if (categories == null || categories.isEmpty()) {
            return;
        }

        Map<Long, String> categoryNameById = categories.stream()
                .filter(category -> category.getId() != null)
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        for (Product product : products) {
            Long categoryId = product.getCategoryId();
            if (categoryId == null) {
                continue;
            }
            product.setCategoryName(categoryNameById.get(categoryId));
        }
    }

    /**
     * 根据ID获取商品详情（管理端）
     *
     * @param id 商品ID
     * @return 商品VO
     * @throws BusinessException 商品不存在时抛出
     */
    @Override
    public ProductVO getProductById(Long id) {
        if (id == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        Product product = productMapper.selectByIdWithCategory(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        return ProductVO.fromEntity(product);
    }

    /**
     * 根据ID获取公开商品详情（小程序端）
     * <p>
     * 仅返回状态为"上架"的商品，用于小程序端商品详情展示。
     *
     * @param id 商品ID
     * @return 商品VO
     * @throws BusinessException 商品不存在或未上架时抛出
     */
    @Override
    public ProductVO getPublicProductById(Long id) {
        if (id == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        Product product = productMapper.selectByIdWithCategory(id);
        if (product == null || !PUBLIC_PRODUCT_STATUS.equals(product.getStatus())) {
            throw new BusinessException(404, "商品不存在");
        }

        return ProductVO.fromEntity(product);
    }

    /**
     * 删除商品（逻辑删除）
     *
     * @param id 商品ID
     * @throws BusinessException 商品不存在或删除失败时抛出
     */
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

    /**
     * 更新商品视频提取信息
     * <p>
     * 由异步视频提取服务调用，用于更新提取结果到商品记录。
     *
     * @param id        商品ID
     * @param videoUrl  提取到的视频URL（成功时有值）
     * @param status    提取状态（SUCCESS/FAILED/NEED_DYNAMIC_RENDER等）
     * @param message   状态描述信息
     * @param targetUrl 目标网址（用于失败后分析或补充规则）
     * @return 更新后的商品VO
     * @throws BusinessException 商品不存在或更新失败时抛出
     */
    @Override
    @Transactional
    public ProductVO updateVideoExtractInfo(Long id, String videoUrl, String status, String message, String targetUrl) {
        if (id == null) {
            throw new BusinessException(400, "商品ID不能为空");
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        LambdaUpdateWrapper<Product> update = new LambdaUpdateWrapper<>();
        update.eq(Product::getId, id)
                .set(Product::getVideoUrl, videoUrl)
                .set(Product::getVideoExtractStatus, status)
                .set(Product::getVideoExtractMessage, message)
                .set(Product::getVideoExtractTargetUrl, targetUrl);

        int result = productMapper.update(null, update);
        if (result <= 0) {
            throw new BusinessException(500, "更新视频提取信息失败");
        }

        log.info("商品视频提取信息更新成功: id={}, status={}, targetUrl={}, videoUrl={}", id, status, targetUrl, videoUrl);
        return ProductVO.fromEntity(productMapper.selectByIdWithCategory(id));
    }
}
