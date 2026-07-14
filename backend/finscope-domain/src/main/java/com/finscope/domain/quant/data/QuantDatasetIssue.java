package com.finscope.domain.quant.data;

import lombok.Data;

import java.time.LocalDate;

@Data
public class QuantDatasetIssue {
    private Long id;
    private Long datasetId;
    private String severity;
    private String issueCode;
    private LocalDate tradeDate;
    private String instrumentCode;
    private String message;
    private int issueCount;
}
