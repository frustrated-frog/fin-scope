package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
public class CapitalFactorObservation {
    private String factorCode;
    private String label;
    private String category;
    private LocalDateTime observedAt;
    private String window;
    private BigDecimal value;
    private BigDecimal baseline;
    private BigDecimal percentile;
    private BigDecimal zScore;
    private String state;
    private int sampleCount;
    private List<String> metricRefs = Collections.emptyList();
    private String qualityStatus;
    private String calculationVersion;
    private String interpretationBoundary;

    public String factorRef() {
        return "factor:" + factorCode + ":" + observedAt;
    }

    public void setMetricRefs(List<String> values) {
        metricRefs = Collections.unmodifiableList(new ArrayList<String>(values == null
                ? Collections.<String>emptyList() : values));
    }
}
