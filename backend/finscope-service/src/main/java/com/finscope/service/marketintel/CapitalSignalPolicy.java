package com.finscope.service.marketintel;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public final class CapitalSignalPolicy {
    private final BigDecimal amountExpansionRatio;
    private final BigDecimal lowAmountRatio;
    private final BigDecimal lateSessionShare;

    public CapitalSignalPolicy() {
        this(new BigDecimal("1.50"), new BigDecimal("0.70"), new BigDecimal("0.40"));
    }

    private CapitalSignalPolicy(BigDecimal amountExpansionRatio, BigDecimal lowAmountRatio, BigDecimal lateSessionShare) {
        this.amountExpansionRatio = amountExpansionRatio;
        this.lowAmountRatio = lowAmountRatio;
        this.lateSessionShare = lateSessionShare;
    }

    public static CapitalSignalPolicy v1() {
        return new CapitalSignalPolicy(new BigDecimal("1.50"), new BigDecimal("0.70"), new BigDecimal("0.40"));
    }

    public BigDecimal getAmountExpansionRatio() {
        return amountExpansionRatio;
    }

    public BigDecimal getLowAmountRatio() {
        return lowAmountRatio;
    }

    public BigDecimal getLateSessionShare() {
        return lateSessionShare;
    }

    public String version() {
        return "capital-signal-v1";
    }
}
