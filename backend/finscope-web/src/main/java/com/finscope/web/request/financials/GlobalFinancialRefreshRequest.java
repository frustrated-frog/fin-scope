package com.finscope.web.request.financials;

import com.finscope.domain.financials.FinancialReportType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class GlobalFinancialRefreshRequest {
    @NotBlank(message = "公司目录来源不能为空")
    private String providerCode;
    @NotBlank(message = "公司主体标识不能为空")
    private String providerCompanyId;
    @NotBlank(message = "公司名称不能为空")
    private String displayName;
    @NotBlank(message = "股票代码不能为空")
    private String symbol;
    private String exchange;
    @NotNull(message = "报告期不能为空")
    private LocalDate periodEnd;
    @NotNull(message = "报告类型不能为空")
    private FinancialReportType reportType;
}
