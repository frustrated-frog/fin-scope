package com.finscope.domain.quant.forecast;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class NextSessionValidationSummary {
    private long recordCount;
    private List<Map<String, Object>> groups;
}
