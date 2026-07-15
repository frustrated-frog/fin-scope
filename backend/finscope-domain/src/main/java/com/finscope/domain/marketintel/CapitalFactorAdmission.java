package com.finscope.domain.marketintel;

import lombok.Data;

/** 候选因子准入审计，不参与在线公式执行。 */
@Data
public class CapitalFactorAdmission {
    private String factorCode;
    private CapitalFactorDefinition.AdmissionStatus status;
    private String reason;
    private String reviewer;
}
