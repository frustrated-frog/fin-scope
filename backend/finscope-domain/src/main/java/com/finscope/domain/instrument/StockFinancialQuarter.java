package com.finscope.domain.instrument;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 同口径累计财报派生的单季度金额；金额已按原始币种归一化，不再换算单位。 */
@Data
public class StockFinancialQuarter {
    private LocalDate periodEnd;
    private FinancialReportType reportType;
    private String currency;
    private Long reportId;
    private String sourceCode;
    private FinancialQualityStatus qualityStatus;
    private LocalDateTime publishedAt;
    private BigDecimal revenue;
    private BigDecimal netProfitParent;
    private BigDecimal operatingCashFlow;
    private BigDecimal grossMarginPct;
    private List<String> dataGaps = new ArrayList<String>();
}
