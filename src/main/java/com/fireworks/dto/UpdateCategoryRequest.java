package com.fireworks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新分类请求 DTO
 */
@Data
public class UpdateCategoryRequest {

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50个字符")
    private String name;

    /**
     * 分类状态: ACTIVE-启用, DISABLED-禁用
     */
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "分类状态不合法")
    private String status;
}
