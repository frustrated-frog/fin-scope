package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class FrozenCandidate {
    private String instrumentCode;
    private String industry;
    private double rankingScore;
    private Double predictedPriceReturn;
    private boolean eligible;
    private String rejectionReason;
}
