package com.fireworks.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fireworks.config.JwtAuthInterceptor;
import com.fireworks.dto.CreateCategoryRequest;
import com.fireworks.dto.UpdateCategoryRequest;
import com.fireworks.service.CategoryService;
import com.fireworks.vo.CategoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 分类控制器单元测试
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtAuthInterceptor jwtAuthInterceptor;

    private CategoryVO testCategoryVO;

    @BeforeEach
    void setUp() throws Exception {
        // Mock interceptor to allow all requests
        when(jwtAuthInterceptor.preHandle(any(HttpServletRequest.class), any(HttpServletResponse.class), any()))
                .thenReturn(true);

        testCategoryVO = CategoryVO.builder()
                .id(1L)
                .name("礼花类")
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("获取所有分类 - 成功")
    void getAllCategories_Success() throws Exception {
        // Arrange
        CategoryVO category2 = CategoryVO.builder()
                .id(2L)
                .name("烟花类")
                .status("ACTIVE")
                .build();
        List<CategoryVO> categories = Arrays.asList(testCategoryVO, category2);
        when(categoryService.getAllCategories()).thenReturn(categories);

        // Act & Assert
        mockMvc.perform(get("/api/v1/categories")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("礼花类"))
                .andExpect(jsonPath("$.data[1].name").value("烟花类"));
    }

    @Test
    @DisplayName("获取启用状态的分类 - 成功")
    void getActiveCategories_Success() throws Exception {
        // Arrange
        List<CategoryVO> categories = Arrays.asList(testCategoryVO);
        when(categoryService.getActiveCategories()).thenReturn(categories);

        // Act & Assert
        mockMvc.perform(get("/api/v1/categories/active")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("获取分类详情 - 成功")
    void getCategoryById_Success() throws Exception {
        // Arrange
        when(categoryService.getCategoryById(1L)).thenReturn(testCategoryVO);

        // Act & Assert
        mockMvc.perform(get("/api/v1/categories/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("礼花类"));
    }

    @Test
    @DisplayName("创建分类 - 成功")
    void createCategory_Success() throws Exception {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("新分类");

        CategoryVO newCategory = CategoryVO.builder()
                .id(10L)
                .name("新分类")
                .status("ACTIVE")
                .build();

        when(categoryService.createCategory(any(CreateCategoryRequest.class))).thenReturn(newCategory);

        // Act & Assert
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("创建成功"))
                .andExpect(jsonPath("$.data.name").value("新分类"));
    }

    @Test
    @DisplayName("创建分类 - 参数校验失败（名称为空）")
    void createCategory_ValidationFailed() throws Exception {
        // Arrange
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName("");  // Empty name

        // Act & Assert
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("更新分类 - 成功")
    void updateCategory_Success() throws Exception {
        // Arrange
        UpdateCategoryRequest request = new UpdateCategoryRequest();
        request.setName("更新后的分类");
        request.setStatus("DISABLED");

        CategoryVO updatedCategory = CategoryVO.builder()
                .id(1L)
                .name("更新后的分类")
                .status("DISABLED")
                .build();

        when(categoryService.updateCategory(eq(1L), any(UpdateCategoryRequest.class))).thenReturn(updatedCategory);

        // Act & Assert
        mockMvc.perform(put("/api/v1/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("更新成功"))
                .andExpect(jsonPath("$.data.name").value("更新后的分类"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("删除分类 - 成功")
    void deleteCategory_Success() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/v1/categories/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("删除成功"));
    }

    @Test
    @DisplayName("获取分类下商品数量 - 成功")
    void getProductCount_Success() throws Exception {
        // Arrange
        when(categoryService.countProductsByCategoryId(1L)).thenReturn(5);

        // Act & Assert
        mockMvc.perform(get("/api/v1/categories/1/product-count")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(5));
    }
}
