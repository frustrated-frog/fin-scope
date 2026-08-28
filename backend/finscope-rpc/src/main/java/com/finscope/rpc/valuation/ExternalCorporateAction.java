package com.finscope.rpc.valuation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExternalCorporateAction {
    private LocalDate exDate;
    private List<String> eventTypes = new ArrayList<String>();
    private BigDecimal dividendPerShare;
    private BigDecimal perShareBonus;
    private BigDecimal allotmentRatio;
    private BigDecimal allotmentPrice;
    private String currency;
    private String sourceCode;
}
