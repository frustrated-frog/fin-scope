package com.finscope.domain.quant.data;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A capital-flow fact frozen into a quant research dataset.
 */
@Data
public class QuantCapitalFlowDaily {
    private Long datasetId;
    private LocalDate tradeDate;
    private String instrumentCode;
    private LocalDateTime availableAt;
    private Long sourceFlowId;
    private String providerCode;
    private BigDecimal mainNetInflow;
    private BigDecimal mainFlowShare;
    private BigDecimal superLargeNetInflow;
    private BigDecimal largeNetInflow;
    private BigDecimal mediumNetInflow;
    private BigDecimal smallNetInflow;
    private BigDecimal turnoverRate;
    private BigDecimal amount;
    private String qualityStatus;
    private String sourceFingerprint;
    private String calculationVersion;
}
