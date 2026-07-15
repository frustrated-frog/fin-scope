package com.finscope.domain.marketintel;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public final class CapitalFactorResult {
    private final String factorVersion;
    private final List<CapitalFactorObservation> observations;
    private final List<String> dataGaps;

    public CapitalFactorResult(String factorVersion,
                               List<CapitalFactorObservation> observations,
                               List<String> dataGaps) {
        this.factorVersion = factorVersion;
        this.observations = Collections.unmodifiableList(new ArrayList<CapitalFactorObservation>(observations));
        this.dataGaps = Collections.unmodifiableList(new ArrayList<String>(dataGaps));
    }

    public Optional<CapitalFactorObservation> find(String code) {
        return observations.stream().filter(item -> item.getFactorCode().equals(code)).findFirst();
    }
}
