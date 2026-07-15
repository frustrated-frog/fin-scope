package com.finscope.domain.marketintel;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalInterpretationObservation {
    private String dimension;
    private String claim;
    private List<String> factorRefs = Collections.emptyList();
    private List<String> metricRefs = Collections.emptyList();
    private List<String> evaluationRefs = Collections.emptyList();

    public void setFactorRefs(List<String> values) { factorRefs = immutable(values); }
    public void setMetricRefs(List<String> values) { metricRefs = immutable(values); }
    public void setEvaluationRefs(List<String> values) { evaluationRefs = immutable(values); }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null
                ? Collections.<T>emptyList() : values));
    }
}
