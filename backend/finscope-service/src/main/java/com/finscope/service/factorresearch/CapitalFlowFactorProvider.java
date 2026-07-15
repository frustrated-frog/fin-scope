package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorObservation;
import com.finscope.domain.factorresearch.ObservationQuality;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Set;
import java.util.Objects;

@Component
public class CapitalFlowFactorProvider implements FactorProvider {
    public static final FactorIdentity MAIN_FLOW_SHARE =
            new FactorIdentity("capital", "MAIN_FLOW_SHARE", "1.0.0");

    public Set<FactorIdentity> factors() { return Collections.singleton(MAIN_FLOW_SHARE); }

    public FactorObservation calculate(FactorCalculationContext context, FactorIdentity factor) {
        if (!MAIN_FLOW_SHARE.equals(factor)) throw new IllegalArgumentException("unsupported capital factor: " + factor);
        QuantCapitalFlowDaily source = context.getCapitalFlow();
        boolean complete = source != null && "COMPLETE".equals(source.getQualityStatus())
                && String.valueOf(source.getDatasetId()).equals(context.getDatasetId())
                && Objects.equals(source.getTradeDate(), context.getTradeDate())
                && Objects.equals(source.getInstrumentCode(), context.getInstrumentCode())
                && source.getAvailableAt() != null && !source.getAvailableAt().isAfter(context.getAvailableAt())
                && source.getMainNetInflow() != null && source.getAmount() != null
                && source.getAmount().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal value = complete ? source.getMainNetInflow().divide(
                source.getAmount(), 10, RoundingMode.HALF_UP) : null;
        String sourceFingerprint = source == null || source.getSourceFingerprint() == null
                ? "missing:" + context.getDatasetId() + ":" + context.getTradeDate()
                : source.getSourceFingerprint();
        return new FactorObservation(context.getDatasetId(), context.getInstrumentCode(), context.getTradeDate(),
                source == null || source.getAvailableAt() == null ? context.getAvailableAt() : source.getAvailableAt(),
                factor, value, value, complete ? ObservationQuality.COMPLETE : ObservationQuality.MISSING_INPUT,
                sourceFingerprint, providerCode() + ":" + calculationVersion() + ":" + sourceFingerprint);
    }

    public String providerCode() { return "FROZEN_CAPITAL_FLOW"; }
    public String calculationVersion() { return "main-flow-share-v1"; }
}
