package com.fireworks.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应 VO
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {

    /**
     * 数据列表
     */
    private List<T> items;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页数量
     */
    private Integer size;

    /**
     * 构建分页响应
     */
    public static <T> PageVO<T> of(List<T> items, Long total, Integer page, Integer size) {
        return new PageVO<>(items, total, page, size);
    }
}
