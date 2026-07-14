package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class CapitalBehaviorSignal {
    private String type;
    private String version;
    private String window;
    private List<String> metricRefs = Collections.emptyList();
    private Map<String, BigDecimal> actualValues = Collections.emptyMap();
    private Map<String, BigDecimal> thresholds = Collections.emptyMap();

    public static CapitalBehaviorSignal of(String type, String version, List<String> metricRefs) {
        CapitalBehaviorSignal signal = new CapitalBehaviorSignal();
        signal.type = type;
        signal.version = version;
        signal.metricRefs = immutable(metricRefs);
        return signal;
    }

    public void setMetricRefs(List<String> metricRefs) { this.metricRefs = immutable(metricRefs); }
    public void setActualValues(Map<String, BigDecimal> values) {
        this.actualValues = Collections.unmodifiableMap(new LinkedHashMap<String, BigDecimal>(values));
    }
    public void setThresholds(Map<String, BigDecimal> values) {
        this.thresholds = Collections.unmodifiableMap(new LinkedHashMap<String, BigDecimal>(values));
    }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
}
