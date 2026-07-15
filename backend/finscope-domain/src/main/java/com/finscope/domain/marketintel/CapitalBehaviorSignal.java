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
    /**
     * 类型。
     */
    private String type;
    /** 面向用户的稳定标签。 */
    private String label;
    /**
     * 版本。
     */
    private String version;
    /**
     * 观察窗口。
     */
    private String window;
    /**
     * 指标引用列表。
     */
    private List<String> metricRefs = Collections.emptyList();
    /** 因子观测引用列表。 */
    private List<String> factorRefs = Collections.emptyList();
    /** 信号输入质量。 */
    private String qualityStatus;
    /** 规则版本。 */
    private String ruleVersion;
    /**
     * 实际指标值。
     */
    private Map<String, BigDecimal> actualValues = Collections.emptyMap();
    /**
     * 阈值集合。
     */
    private Map<String, BigDecimal> thresholds = Collections.emptyMap();

    public static CapitalBehaviorSignal of(String type, String version, List<String> metricRefs) {
        CapitalBehaviorSignal signal = new CapitalBehaviorSignal();
        signal.type = type;
        signal.version = version;
        signal.metricRefs = immutable(metricRefs);
        return signal;
    }

    public void setMetricRefs(List<String> metricRefs) { this.metricRefs = immutable(metricRefs); }
    public void setFactorRefs(List<String> factorRefs) { this.factorRefs = immutable(factorRefs); }
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
