package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalBusinessAspect;
import com.finscope.common.enums.fundamentals.FundamentalFindingSupport;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class FundamentalBusinessFinding {
    private FundamentalBusinessAspect aspect;
    private String label;
    private String noteField;
    private String content;
    private FundamentalFindingSupport support;
    private Long runId;
    private LocalDateTime generatedAt;
    private List<FundamentalBusinessSource> sources = new ArrayList<>();
}
