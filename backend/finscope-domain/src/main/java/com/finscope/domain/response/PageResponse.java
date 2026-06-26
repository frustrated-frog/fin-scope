package com.finscope.domain.response;

import lombok.Data;
import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> items;
    private int totalCount;
    private int page;
    private int pageSize;
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
