package com.finscope.domain.valuation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 同日同源的本地同行估值样本，不代表完整行业。 */
@Data
public class StockValuationPeer {
    private String code;
    private String name;
    private LocalDate observedDate;
    private String sourceCode;
    private String qualityStatus;
    private BigDecimal peTtm;
    private BigDecimal pbMrq;
}
