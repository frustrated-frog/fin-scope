package com.finscope.domain.response;

import lombok.Data;
import java.util.List;

@Data
public class PageResponse<T> {
    /**
     * 当前页数据列表。
     */
    private List<T> items;
    /**
     * 总数量。
     */
    private int totalCount;
    /**
     * 当前页码。
     */
    private int page;
    /**
     * 每页条数。
     */
    private int pageSize;
    /**
     * 总页数。
     */
    private int totalPages;

    public static <T> PageResponse<T> of(List<T> items, int totalCount, int page, int pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setItems(items);
        response.setTotalCount(totalCount);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages((int) Math.ceil((double) totalCount / pageSize));
        return response;
    }
}
