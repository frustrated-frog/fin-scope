package com.finscope.domain.financials;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.common.enums.financials.FinancialValueOrigin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinancialLineItem {
    private Long id;
    private Long reportId;
    private FinancialStatementType statementType;
    private String sourceLabel;
    private String conceptCode;
    private String periodRole;
    private BigDecimal normalizedValue;
    private String currency;
    private BigDecimal unitMultiplier = BigDecimal.ONE;
    private FinancialValueOrigin valueOrigin;
    private String sourceField;
    private String sourceCode;
    private Integer displayOrder;
    private FinancialQualityStatus qualityStatus;
}
