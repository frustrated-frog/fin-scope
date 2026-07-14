package com.finscope.domain.quant.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class QuantDailyBar {
    private Long id;
    private Long datasetId;
    private LocalDate tradeDate;
    private String instrumentCode;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal adjustedClose;
    private BigDecimal volume;
    private BigDecimal amount;
    private String tradeStatus;
    private boolean st;
    private boolean limitUp;
    private boolean limitDown;
}
