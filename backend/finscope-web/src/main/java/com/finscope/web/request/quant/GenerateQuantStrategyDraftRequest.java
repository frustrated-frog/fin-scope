package com.finscope.web.request.quant;

import lombok.Data;

@Data
public class GenerateQuantStrategyDraftRequest {
    private Long datasetId;
    private String prompt;
}
