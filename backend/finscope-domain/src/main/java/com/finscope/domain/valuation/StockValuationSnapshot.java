package com.finscope.domain.valuation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockValuationSnapshot {
    private Long id;
    private Long instrumentId;
    private LocalDate observedDate;
    private Instant observedAt;
    private String name;
    private BigDecimal peTtm;
    private BigDecimal peMrq;
    private BigDecimal pbMrq;
    private BigDecimal psTtm;
    private BigDecimal pcfTtm;
    private String sourceCode;
    private String qualityStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
