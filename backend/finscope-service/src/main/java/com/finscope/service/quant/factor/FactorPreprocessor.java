package com.finscope.service.quant.factor;

import com.finscope.domain.quant.factor.FactorValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FactorPreprocessor {
    public Map<String, Double> normalize(List<FactorValue> values) {
        List<Double> finite = new ArrayList<Double>();
        for (FactorValue value : values) if (Double.isFinite(value.getValue())) finite.add(value.getValue());
        if (finite.isEmpty()) return Collections.emptyMap();
        if (finite.size() == 1) {
            Map<String, Double> singleton = new LinkedHashMap<String, Double>();
            for (FactorValue value : values) if (Double.isFinite(value.getValue())) singleton.put(value.getInstrumentCode(), 0d);
            return singleton;
        }
        Collections.sort(finite);
        double low = percentile(finite, 0.025); double high = percentile(finite, 0.975);
        List<Double> clipped = new ArrayList<Double>();
        for (FactorValue value : values) if (Double.isFinite(value.getValue())) clipped.add(clip(value.getValue(), low, high));
        double mean = clipped.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sum = 0; for (double item : clipped) sum += (item - mean) * (item - mean);
        double std = Math.sqrt(sum / clipped.size());
        if (std == 0) return Collections.emptyMap();
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        for (FactorValue value : values) {
            if (Double.isFinite(value.getValue())) result.put(value.getInstrumentCode(), (clip(value.getValue(), low, high) - mean) / std);
        }
        return result;
    }

    private double percentile(List<Double> sorted, double percentile) {
        double index = percentile * (sorted.size() - 1); int lower = (int) Math.floor(index); int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        return sorted.get(lower) * (upper - index) + sorted.get(upper) * (index - lower);
    }
    private double clip(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
}
