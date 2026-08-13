package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorObservation;
import com.finscope.common.enums.factorresearch.ObservationQuality;
import com.finscope.domain.quant.factor.FactorDefinition;
import com.finscope.service.quant.factor.FactorCalculator;
import com.finscope.service.quant.factor.FactorRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class LegacyQuantFactorProvider implements FactorProvider {
    private final FactorRegistry registry = new FactorRegistry();
    private final FactorCalculator calculator = new FactorCalculator();

    public Set<FactorIdentity> factors() {
        Set<FactorIdentity> result = new LinkedHashSet<FactorIdentity>();
        for (FactorDefinition definition : registry.list()) {
            result.add(new FactorIdentity("quant", definition.getCode(), "1.0.0"));
        }
        return result;
    }

    public FactorObservation calculate(FactorCalculationContext context, FactorIdentity factor) {
        if (!factors().contains(factor)) throw new IllegalArgumentException("unsupported legacy factor: " + factor);
        double calculated = calculator.value(factor.getCode(), context.getHistory(), context.getFundamental());
        boolean complete = Double.isFinite(calculated);
        BigDecimal value = complete ? BigDecimal.valueOf(calculated) : null;
        String source = "dataset:" + context.getDatasetId() + ":" + context.getTradeDate()
                + ":" + context.getInstrumentCode();
        return new FactorObservation(context.getDatasetId(), context.getInstrumentCode(),
                context.getTradeDate(), context.getAvailableAt(), factor, value, value,
                complete ? ObservationQuality.COMPLETE : ObservationQuality.MISSING_INPUT,
                source, providerCode() + ":" + calculationVersion() + ":" + factor);
    }

    public String providerCode() { return "LEGACY_QUANT"; }
    public String calculationVersion() { return "factor-calculator-v1"; }

}
