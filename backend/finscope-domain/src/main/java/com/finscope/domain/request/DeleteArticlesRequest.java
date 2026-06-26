package com.finscope.domain.request;

import lombok.Data;
import java.util.List;

@Data
public class DeleteArticlesRequest {
    private List<Long> ids;
}
