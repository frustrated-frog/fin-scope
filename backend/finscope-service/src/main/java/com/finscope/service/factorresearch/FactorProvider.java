package com.finscope.service.factorresearch;

import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorObservation;

import java.util.Set;

public interface FactorProvider {
    Set<FactorIdentity> factors();
    FactorObservation calculate(FactorCalculationContext context, FactorIdentity factor);
    String providerCode();
    String calculationVersion();
}
