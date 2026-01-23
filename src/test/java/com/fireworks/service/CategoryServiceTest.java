package com.fireworks.service;

import com.fireworks.dto.CreateCategoryRequest;
import com.fireworks.dto.UpdateCategoryRequest;
import com.fireworks.entity.Category;
import com.fireworks.exception.BusinessException;
import com.fireworks.mapper.CategoryMapper;
import com.fireworks.service.impl.CategoryServiceImpl;
import com.fireworks.vo.CategoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 分类服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = new Category();
        testCategory.setId(1L);
        testCategory.setName("礼花类");
        testCategory.setStatus("ACTIVE");
        testCategory.setCreatedAt(LocalDateTime.now());
        testCategory.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("获取所有分类 - 成功")
    void getAllCategories_Success() {
        // Arrange
        Category category2 = new Category();
        category2.setId(2L);
        category2.setName("烟花类");
        category2.setStatus("ACTIVE");

        when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(testCategory, category2));

        // Act
        List<CategoryVO> result = categoryService.getAllCategories();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("礼花类", result.get(0).getName());
        assertEquals("烟花类", result.get(1).getName());
    }

    @Test
    @DisplayName("获取启用状态的分类 - 成功")
    void getActiveCategories_Success() {
        // Arrange
        when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(testCategory));

        // Act
        List<CategoryVO> result = categoryService.getActiveCategories();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    @DisplayName("根据ID获取分类 - 成功")
    void getCategoryById_Success() {
        // Arrange
        when(categoryMapper.selectById(1L)).thenReturn(testCategory);

        // Act
        CategoryVO result = categoryService.getCategoryById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("礼花类", result.getName());
    }

    @Test
    @DisplayName("根据ID获取分类 - 分类不存在")
    void getCategoryById_NotFound() {
        // Arrange
        when(categoryMapper.selectById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> categoryService.getCategoryById(999L));
        assertEquals(404, exception.getCode());
        assertEquals("分类不存在", exception.getMessage());
    }

    @Test
    @DisplayName("创建分类 - 成功")
    void createCategory_Success() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("新分类");

        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.insert(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(10L);
            return 1;
        });

        // Act
        CategoryVO result = categoryService.createCategory(request);

        // Assert
        assertNotNull(result);
        assertEquals("新分类", result.getName());
        assertEquals("ACTIVE", result.getStatus());
        verify(categoryMapper, times(1)).insert(any(Category.class));
    }

    @Test
    @DisplayName("创建分类 - 名称已存在")
    void createCategory_NameExists() {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("礼花类");

        when(categoryMapper.selectCount(any())).thenReturn(1L);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> categoryService.createCategory(request));
        assertEquals(400, exception.getCode());
        assertEquals("分类名称已存在", exception.getMessage());
    }

    @Test
    @DisplayName("更新分类 - 成功")
    void updateCategory_Success() {
        // Arrange
        UpdateCategoryRequest request = new UpdateCategoryRequest();
        request.setName("更新后的分类");
        request.setStatus("DISABLED");

        when(categoryMapper.selectById(1L)).thenReturn(testCategory);
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.updateById(any(Category.class))).thenReturn(1);

        // Act
        CategoryVO result = categoryService.updateCategory(1L, request);

        // Assert
        assertNotNull(result);
        assertEquals("更新后的分类", result.getName());
        assertEquals("DISABLED", result.getStatus());
    }

    @Test
    @DisplayName("更新分类 - 分类不存在")
    void updateCategory_NotFound() {
        // Arrange
        UpdateCategoryRequest request = new UpdateCategoryRequest();
        request.setName("更新后的分类");

        when(categoryMapper.selectById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> categoryService.updateCategory(999L, request));
        assertEquals(404, exception.getCode());
    }

    @Test
    @DisplayName("删除分类 - 成功")
    void deleteCategory_Success() {
        // Arrange
        when(categoryMapper.selectById(1L)).thenReturn(testCategory);
        when(categoryMapper.countProductsByCategoryId(1L)).thenReturn(0);
        when(categoryMapper.deleteById(1L)).thenReturn(1);

        // Act
        categoryService.deleteCategory(1L);

        // Assert
        verify(categoryMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("删除分类 - 有关联商品")
    void deleteCategory_HasProducts() {
        // Arrange
        when(categoryMapper.selectById(1L)).thenReturn(testCategory);
        when(categoryMapper.countProductsByCategoryId(1L)).thenReturn(5);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> categoryService.deleteCategory(1L));
        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("5 个商品"));
    }

    @Test
    @DisplayName("删除分类 - 分类不存在")
    void deleteCategory_NotFound() {
        // Arrange
        when(categoryMapper.selectById(999L)).thenReturn(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
            () -> categoryService.deleteCategory(999L));
        assertEquals(404, exception.getCode());
    }
}
