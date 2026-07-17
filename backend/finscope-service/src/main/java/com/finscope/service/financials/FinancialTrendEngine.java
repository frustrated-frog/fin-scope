package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinancialTrendEngine {
    public List<FinancialEvidence> build(List<FinancialReportView> views) {
        List<FinancialReportView> sorted = new ArrayList<FinancialReportView>(views);
        sorted.sort(Comparator.comparing(value -> value.getReport().getPeriodEnd()));
        Map<String, Series> annual = new LinkedHashMap<String, Series>();
        Map<String, Series> quarters = new LinkedHashMap<String, Series>();
        for (FinancialReportView view : sorted) {
            if (view.getReport().getReportType() == FinancialReportType.ANNUAL) {
                for (FinancialMetric metric : view.getMetrics()) {
                    if (metric.getValue() == null || metric.getMetricCode() == null) continue;
                    Series series = annual.computeIfAbsent(metric.getMetricCode(),
                            code -> new Series(metric.getLabel(), metric.getUnit()));
                    series.add(view.getReport().getPeriodEnd().toString(),
                            metric.getValue().toPlainString());
                }
            } else {
                view.getStatements().values().forEach(items -> {
                    for (FinancialLineItem item : items) {
                        if (!"CURRENT_QUARTER".equals(item.getPeriodRole())
                                || item.getConceptCode() == null || item.getNormalizedValue() == null) continue;
                        Series series = quarters.computeIfAbsent(item.getConceptCode(),
                                code -> new Series(item.getSourceLabel(), item.getCurrency()));
                        series.add(view.getReport().getPeriodEnd().toString(),
                                item.getNormalizedValue().toPlainString());
                    }
                });
            }
        }
        List<FinancialEvidence> result = new ArrayList<FinancialEvidence>();
        annual.forEach((code, series) -> {
            if (series.points.size() >= 2) {
                result.add(evidence("T_" + token(code) + "_ANNUAL", series, 5));
            }
        });
        quarters.forEach((code, series) -> result.add(
                evidence("T_" + token(code) + "_QUARTER", series, 8)));
        result.sort(Comparator.comparing(FinancialEvidence::getId));
        return result;
    }

    private FinancialEvidence evidence(String id, Series series, int limit) {
        FinancialEvidence value = new FinancialEvidence();
        value.setId(id);
        value.setType("TREND");
        value.setLabel(series.label + (id.endsWith("ANNUAL") ? "年度趋势" : "单季度趋势"));
        value.setUnit(series.unit);
        int from = Math.max(0, series.points.size() - limit);
        value.setDetail(String.join(";", series.points.subList(from, series.points.size())));
        value.setValue(value.getDetail());
        return value;
    }

    private static String token(String value) {
        return value.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    private static final class Series {
        private final String label;
        private final String unit;
        private final List<String> points = new ArrayList<String>();

        private Series(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }

        private void add(String period, String value) {
            points.add(period + "=" + value);
        }
    }
}
