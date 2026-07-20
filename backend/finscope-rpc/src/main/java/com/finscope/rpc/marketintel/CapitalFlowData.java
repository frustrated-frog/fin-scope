package com.finscope.rpc.marketintel;

import com.finscope.domain.marketintel.CapitalFlowPoint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CapitalFlowData {
    private final List<CapitalFlowPoint> minutePoints;
    private final List<CapitalFlowPoint> dailyPoints;
    private final BigDecimal turnoverRate;
    private final BigDecimal volumeRatio;
    private final List<String> warnings;
    private final String providerCode;

    public CapitalFlowData(List<CapitalFlowPoint> minutePoints, List<CapitalFlowPoint> dailyPoints,
                           BigDecimal turnoverRate, BigDecimal volumeRatio, List<String> warnings, String providerCode) {
        this.minutePoints = immutable(minutePoints);
        this.dailyPoints = immutable(dailyPoints);
        this.turnoverRate = turnoverRate;
        this.volumeRatio = volumeRatio;
        this.warnings = immutable(warnings);
        this.providerCode = providerCode;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public List<CapitalFlowPoint> getMinutePoints() {
        return minutePoints;
    }

    public List<CapitalFlowPoint> getDailyPoints() {
        return dailyPoints;
    }

    public BigDecimal getTurnoverRate() {
        return turnoverRate;
    }

    public BigDecimal getVolumeRatio() {
        return volumeRatio;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public List<CapitalFlowPoint> allPoints() {
        List<CapitalFlowPoint> all = new ArrayList<CapitalFlowPoint>(minutePoints);
        all.addAll(dailyPoints);
        return all;
    }
}
