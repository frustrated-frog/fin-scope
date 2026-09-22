package com.finscope.domain.quant.forecast;

import com.finscope.common.enums.quant.NextSessionOutcomeStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** All frozen records are deduplicated before inspecting outcomes; every target date has equal weight. */
public final class NextSessionValidationCalculator {
    private NextSessionValidationCalculator() {
    }

    public static NextSessionValidationSummary summarize(List<NextSessionPredictionRecord> records) {
        Map<String, List<NextSessionPredictionRecord>> versions = records.stream().collect(Collectors.groupingBy(
                row -> row.getPrediction().getModelVersion(), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<NextSessionPredictionRecord>> version : versions.entrySet()) {
            List<NextSessionPredictionRecord> rows = new ArrayList<>(version.getValue());
            rows.sort(Comparator.comparing((NextSessionPredictionRecord row) -> row.getPrediction().getGeneratedAt())
                    .thenComparing(NextSessionPredictionRecord::getId));
            Map<String, NextSessionPredictionRecord> unique = new LinkedHashMap<>();
            for (NextSessionPredictionRecord row : rows) {
                unique.putIfAbsent(row.getInstrumentCode() + "/" + row.getPrediction().getTargetDate(), row);
            }
            List<NextSessionPredictionRecord> matured = unique.values().stream()
                    .filter(NextSessionValidationCalculator::valid).collect(Collectors.toList());
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("version", version.getKey());
            group.put("loaded", rows.size());
            group.put("duplicates", rows.size() - unique.size());
            group.put("pending", unique.values().stream().filter(row -> row.getStatus() == NextSessionOutcomeStatus.PENDING).count());
            group.put("unavailable", unique.values().stream().filter(row -> row.getStatus() == NextSessionOutcomeStatus.UNAVAILABLE).count());
            group.put("count", matured.size());
            group.put("days", matured.stream().map(row -> row.getPrediction().getTargetDate()).distinct().count());
            group.put("accuracy", dailyMean(matured, row -> (row.getPrediction().getUpProbability() >= .5) == (row.getActualReturn() > 0) ? 1 : 0));
            group.put("brier", dailyMean(matured, row -> Math.pow(row.getPrediction().getUpProbability() - (row.getActualReturn() > 0 ? 1 : 0), 2)));
            group.put("bins", bins(matured));
            groups.add(group);
        }
        NextSessionValidationSummary summary = new NextSessionValidationSummary();
        summary.setRecordCount(records.size());
        summary.setGroups(groups);
        return summary;
    }

    private static boolean valid(NextSessionPredictionRecord row) {
        Double probability = row.getPrediction().getUpProbability();
        return row.getStatus() == NextSessionOutcomeStatus.MATURED && row.getActualReturn() != null
                && Double.isFinite(row.getActualReturn()) && probability != null && Double.isFinite(probability)
                && probability >= 0 && probability <= 1;
    }

    private static Double dailyMean(List<NextSessionPredictionRecord> rows, ToDoubleFunction<NextSessionPredictionRecord> metric) {
        if (rows.isEmpty()) {
            return null;
        }
        return rows.stream().collect(Collectors.groupingBy(row -> row.getPrediction().getTargetDate(),
                Collectors.averagingDouble(metric))).values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static List<Map<String, Object>> bins(List<NextSessionPredictionRecord> rows) {
        double[] bounds = {0, .4, .5, .6, .7, 1};
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < bounds.length - 1; index++) {
            double lower = bounds[index];
            double upper = bounds[index + 1];
            List<NextSessionPredictionRecord> selected = rows.stream().filter(row -> row.getPrediction().getUpProbability() >= lower
                    && (row.getPrediction().getUpProbability() < upper || upper == 1)).collect(Collectors.toList());
            Map<String, Object> bin = new LinkedHashMap<>();
            bin.put("lower", lower);
            bin.put("upper", upper);
            bin.put("count", selected.size());
            bin.put("days", selected.stream().map(row -> row.getPrediction().getTargetDate()).distinct().count());
            bin.put("forecast", dailyMean(selected, row -> row.getPrediction().getUpProbability()));
            bin.put("actual", dailyMean(selected, row -> row.getActualReturn() > 0 ? 1 : 0));
            result.add(bin);
        }
        return result;
    }
}
