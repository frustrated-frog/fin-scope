package com.finscope.domain.watchlistresearch;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class StockGrowthQuality {
    private String code;
    private String name;
    private String currency;
    private String sourceCode;
    private LocalDate periodFrom;
    private LocalDate periodThrough;
    private BigDecimal revenueTtm;
    private BigDecimal profitTtm;
    private BigDecimal operatingCashFlowTtm;
    private Double revenueGrowthPct;
    private Double profitGrowthPct;
    private Double cashConversion;
    private Double grossMarginPct;
    private Double grossMarginChangePp;
    private List<String> gaps = new ArrayList<String>();
}
