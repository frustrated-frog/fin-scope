package com.finscope.rpc.financials;

import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialStatementType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExternalFinancialStatements {
    private LocalDate periodEnd;
    private FinancialReportType reportType;
    private String scope;
    private String currency;
    private LocalDateTime publishedAt;
    private Boolean audited;
    private String sourceCode;
    private FinancialQualityStatus qualityStatus;
    private List<String> warnings = new ArrayList<String>();
    private List<Statement> statements = new ArrayList<Statement>();

    public Value find(FinancialStatementType statementType, String conceptCode) {
        for (Statement statement : statements) {
            if (statement.getStatementType() != statementType) {
                continue;
            }
            for (Value value : statement.getValues()) {
                if (conceptCode.equals(value.getConceptCode())) {
                    return value;
                }
            }
        }
        return null;
    }

    @Data
    public static class Statement {
        private FinancialStatementType statementType;
        private List<Value> values = new ArrayList<Value>();
    }

    @Data
    public static class Value {
        private String sourceLabel;
        private String conceptCode;
        private String periodRole;
        private BigDecimal value;
        private BigDecimal unitMultiplier = BigDecimal.ONE;
        private String sourceField;
    }
}
