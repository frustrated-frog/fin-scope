package com.finscope.domain.strategy.holding;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StockTransaction {
    private Long id;
    private String clientRequestId;
    private Long instrumentId;
    private String instrumentCode;
    private String instrumentName;
    private StockTransactionType type;
    private LocalDate tradeDate;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal commission;
    private BigDecimal stampDuty;
    private BigDecimal transferFee;
    private BigDecimal otherFee;
    private BigDecimal cashAmount;
    private Long reversalOfId;
    private String note;
    private LocalDateTime createdAt;

    public BigDecimal totalFees() {
        return zero(commission).add(zero(stampDuty)).add(zero(transferFee)).add(zero(otherFee));
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
