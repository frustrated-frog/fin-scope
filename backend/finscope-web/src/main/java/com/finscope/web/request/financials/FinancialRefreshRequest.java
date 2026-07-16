package com.finscope.web.request.financials;

import com.finscope.domain.financials.FinancialReportType;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class FinancialRefreshRequest {
    @NotNull(message = "报告期不能为空")
    private LocalDate periodEnd;

    @NotNull(message = "报告类型不能为空")
    private FinancialReportType reportType;
}
