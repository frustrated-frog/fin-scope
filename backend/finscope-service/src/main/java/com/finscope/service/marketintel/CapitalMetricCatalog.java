package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalEvidenceRef;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 原始资金字段的统一语义目录，禁止将内部字段名直接暴露给 Agent 或页面。
 */
@Component
public class CapitalMetricCatalog {
    private final List<MetricDefinition> definitions;

    public CapitalMetricCatalog() {
        List<MetricDefinition> values = new ArrayList<MetricDefinition>();
        values.add(metric("price", "最新价格", "PRICE", "元", CapitalFlowPoint::getPrice));
        values.add(metric("tradeVolume", "成交量", "VOLUME", "手", CapitalFlowPoint::getTradeVolume));
        values.add(metric("intervalTradeAmount", "区间成交额", "VOLUME", "元", CapitalFlowPoint::getIntervalTradeAmount));
        values.add(metric("cumulativeTradeAmount", "累计成交额", "VOLUME", "元", CapitalFlowPoint::getCumulativeTradeAmount));
        values.add(metric("turnoverRate", "换手率", "TURNOVER", "%", CapitalFlowPoint::getTurnoverRate));
        values.add(metric("volumeRatio", "量比", "VOLUME", "倍", CapitalFlowPoint::getVolumeRatio));
        values.add(metric("mainInflow", "主力流入", "FLOW", "元", CapitalFlowPoint::getMainInflow));
        values.add(metric("mainOutflow", "主力流出", "FLOW", "元", CapitalFlowPoint::getMainOutflow));
        values.add(metric("mainNetInflow", "主力净额", "FLOW", "元", CapitalFlowPoint::getMainNetInflow));
        values.add(metric("superLargeNetInflow", "超大单净额", "ORDER_STRUCTURE", "元", CapitalFlowPoint::getSuperLargeNetInflow));
        values.add(metric("largeNetInflow", "大单净额", "ORDER_STRUCTURE", "元", CapitalFlowPoint::getLargeNetInflow));
        values.add(metric("mediumNetInflow", "中单净额", "ORDER_STRUCTURE", "元", CapitalFlowPoint::getMediumNetInflow));
        values.add(metric("smallNetInflow", "小单净额", "ORDER_STRUCTURE", "元", CapitalFlowPoint::getSmallNetInflow));
        definitions = Collections.unmodifiableList(values);
    }

    public List<CapitalEvidenceRef> evidence(CapitalFlowPoint point) {
        if (point == null || point.getId() == null) return Collections.emptyList();
        List<CapitalEvidenceRef> result = new ArrayList<CapitalEvidenceRef>();
        for (MetricDefinition definition : definitions) {
            BigDecimal value = definition.reader.apply(point);
            if (value != null) {
                result.add(new CapitalEvidenceRef(point.metricRef(definition.code), definition.label,
                        definition.category, value, definition.unit, point.getObservedAt()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private MetricDefinition metric(String code, String label, String category, String unit,
                                    Function<CapitalFlowPoint, BigDecimal> reader) {
        return new MetricDefinition(code, label, category, unit, reader);
    }

    private static final class MetricDefinition {
        private final String code;
        private final String label;
        private final String category;
        private final String unit;
        private final Function<CapitalFlowPoint, BigDecimal> reader;

        private MetricDefinition(String code, String label, String category, String unit,
                                 Function<CapitalFlowPoint, BigDecimal> reader) {
            this.code = code;
            this.label = label;
            this.category = category;
            this.unit = unit;
            this.reader = reader;
        }
    }
}
