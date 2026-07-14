package com.finscope.domain.quant.data;

import lombok.Data;

import java.time.LocalDate;

@Data
public class QuantUniverseMember {
    private Long datasetId;
    private LocalDate tradeDate;
    private String instrumentCode;
    private boolean member = true;
    private String sourceKind;
}
