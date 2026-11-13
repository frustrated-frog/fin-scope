package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalDimension;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 保存时冻结的财报事实，后续刷新报告不覆盖此对象。 */
@Data
public class FundamentalFinancialBaseline {
    private Long reportId;
    private LocalDate periodEnd;
    private String reportType;
    private String scope;
    private String currency;
    private String sourceCode;
    private LocalDateTime publishedAt;
    private FundamentalDimension dimension;
    private List<FundamentalMetric> metrics = new ArrayList<>();
}
