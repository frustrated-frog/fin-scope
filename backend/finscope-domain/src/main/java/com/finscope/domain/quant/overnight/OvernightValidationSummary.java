package com.finscope.domain.quant.overnight;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OvernightValidationSummary {
    private String scope;
    private long recordCount;
    private String protocol;
    private List<Map<String, Object>> groups;
    private List<String> limitations;
}
