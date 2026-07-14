package com.finscope.domain.request;

import lombok.Data;
import java.util.List;

@Data
public class DeleteArticlesRequest {
    /**
     * ID 列表。
     */
    private List<Long> ids;
}
