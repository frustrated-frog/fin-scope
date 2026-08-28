package com.finscope.domain.valuation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockCorporateAction {
    private Long id;
    private Long instrumentId;
    private LocalDate exDate;
    private List<String> eventTypes = new ArrayList<String>();
    private BigDecimal dividendPerShare;
    private BigDecimal perShareBonus;
    private BigDecimal allotmentRatio;
    private BigDecimal allotmentPrice;
    private String currency;
    private String sourceCode;
    private LocalDateTime retrievedAt;
}
