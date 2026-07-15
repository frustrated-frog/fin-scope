package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalFactorObservation;
import com.finscope.domain.marketintel.CapitalFactorResult;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalWatchCondition;
import com.finscope.service.marketintel.factor.CapitalFactorEngine;
import com.finscope.service.marketintel.factor.CapitalFactorRegistry;
import com.finscope.service.quant.factor.TimeSeriesFactorOperators;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CapitalBehaviorSignalService {
    public static final String VERSION = "capital-signal-v2";
    private final CapitalSignalPolicy policy;
    private final CapitalFactorEngine factors;

    public CapitalBehaviorSignalService() {
        this(CapitalSignalPolicy.v2(), new CapitalFactorEngine(
                new CapitalFactorRegistry(), new TimeSeriesFactorOperators()));
    }

    public CapitalBehaviorSignalService(CapitalSignalPolicy policy) {
        this(policy, new CapitalFactorEngine(new CapitalFactorRegistry(), new TimeSeriesFactorOperators()));
    }

    @Autowired
    public CapitalBehaviorSignalService(CapitalSignalPolicy policy, CapitalFactorEngine factors) {
        this.policy = policy;
        this.factors = factors;
    }

    public List<CapitalBehaviorSignal> detect(List<CapitalFlowPoint> facts) {
        return detect(factors.calculate(facts));
    }

    public List<CapitalBehaviorSignal> detect(CapitalFactorResult factorResult) {
        List<CapitalBehaviorSignal> result = new ArrayList<CapitalBehaviorSignal>();
        Optional<CapitalFactorObservation> amount = factorResult.find("AMOUNT_RATIO_5D");
        Optional<CapitalFactorObservation> flow = factorResult.find("MAIN_FLOW_SHARE");
        if (amount.isPresent() && flow.isPresent()) {
            if (amount.get().getValue().compareTo(policy.getAmountExpansionRatio()) >= 0) {
                result.add(signal(flow.get().getValue().signum() >= 0 ? "AMOUNT_EXPANSION_WITH_INFLOW"
                                : "AMOUNT_EXPANSION_WITH_OUTFLOW",
                        flow.get().getValue().signum() >= 0 ? "放量净流入" : "放量净流出",
                        Arrays.asList(amount.get(), flow.get()), "5d", "AMOUNT_RATIO_5D",
                        policy.getAmountExpansionRatio()));
            } else if (amount.get().getValue().compareTo(policy.getLowAmountRatio()) <= 0) {
                result.add(signal(flow.get().getValue().signum() >= 0 ? "LOW_AMOUNT_INFLOW" : "LOW_AMOUNT_OUTFLOW",
                        flow.get().getValue().signum() >= 0 ? "缩量净流入" : "缩量净流出",
                        Arrays.asList(amount.get(), flow.get()), "5d", "AMOUNT_RATIO_5D",
                        policy.getLowAmountRatio()));
            }
        }
        addStateSignal(result, factorResult, "PRICE_FLOW_ALIGNMENT", "DIVERGENT",
                "PRICE_FLOW_DIVERGENCE", "价格与资金背离");
        addStateSignal(result, factorResult, "BIG_SMALL_DIVERGENCE", "DIVERGENT",
                "BIG_SMALL_ORDER_DIVERGENCE", "大单与中小单背离");
        addStateSignal(result, factorResult, "INTRADAY_FLOW_REVERSALS", "REVERSAL",
                "INTRADAY_FLOW_REVERSAL", "日内资金方向反转");
        Optional<CapitalFactorObservation> late = factorResult.find("LATE_SESSION_FLOW_SHARE");
        if (late.isPresent() && late.get().getValue().abs().compareTo(policy.getLateSessionShare()) >= 0) {
            result.add(signal(late.get().getValue().signum() >= 0 ? "LATE_SESSION_INFLOW" : "LATE_SESSION_OUTFLOW",
                    late.get().getValue().signum() >= 0 ? "尾盘流入增强" : "尾盘流出增强",
                    Collections.singletonList(late.get()), "session", "LATE_SESSION_FLOW_SHARE",
                    policy.getLateSessionShare()));
        }
        Optional<CapitalFactorObservation> acceleration = factorResult.find("INTRADAY_FLOW_ACCELERATION");
        if (acceleration.isPresent() && !"STABLE".equals(acceleration.get().getState())) {
            result.add(signal(acceleration.get().getValue().signum() >= 0 ? "INTRADAY_ACCELERATING_INFLOW"
                            : "INTRADAY_ACCELERATING_OUTFLOW",
                    acceleration.get().getValue().signum() >= 0 ? "日内资金加速流入" : "日内资金加速流出",
                    Collections.singletonList(acceleration.get()), "last3", null, null));
        }
        return Collections.unmodifiableList(result);
    }

    public List<CapitalWatchCondition> watchConditions(CapitalFactorResult factorResult) {
        List<CapitalWatchCondition> values = new ArrayList<CapitalWatchCondition>();
        addCondition(values, factorResult, "AMOUNT_RATIO_5D", "量能比达到放量阈值", ">=",
                policy.getAmountExpansionRatio(), "倍");
        addCondition(values, factorResult, "MAIN_FLOW_SHARE", "主力净额占比转正", ">",
                BigDecimal.ZERO, "比例");
        addCondition(values, factorResult, "LATE_SESSION_FLOW_SHARE", "尾盘资金占比超过观察阈值", "ABS>=",
                policy.getLateSessionShare(), "比例");
        return Collections.unmodifiableList(values);
    }

    private void addStateSignal(List<CapitalBehaviorSignal> result, CapitalFactorResult factors,
                                String factorCode, String state, String code, String label) {
        Optional<CapitalFactorObservation> value = factors.find(factorCode);
        if (value.isPresent() && state.equals(value.get().getState())) {
            result.add(signal(code, label, Collections.singletonList(value.get()), value.get().getWindow(), null, null));
        }
    }

    private CapitalBehaviorSignal signal(String code, String label, List<CapitalFactorObservation> observations,
                                         String window, String thresholdCode, BigDecimal threshold) {
        List<String> factorRefs = new ArrayList<String>();
        List<String> metricRefs = new ArrayList<String>();
        Map<String, BigDecimal> actual = new LinkedHashMap<String, BigDecimal>();
        boolean complete = true;
        for (CapitalFactorObservation observation : observations) {
            factorRefs.add(observation.factorRef());
            metricRefs.addAll(observation.getMetricRefs());
            actual.put(observation.getFactorCode(), observation.getValue());
            complete = complete && "COMPLETE".equals(observation.getQualityStatus());
        }
        CapitalBehaviorSignal value = CapitalBehaviorSignal.of(
                code, VERSION, CapitalEvidenceRefs.recentDistinct(metricRefs));
        value.setLabel(label);
        value.setWindow(window);
        value.setFactorRefs(factorRefs);
        value.setActualValues(actual);
        Map<String, BigDecimal> thresholds = new LinkedHashMap<String, BigDecimal>();
        if (thresholdCode != null && threshold != null) thresholds.put(thresholdCode, threshold);
        value.setThresholds(thresholds);
        value.setQualityStatus(complete ? "COMPLETE" : "PARTIAL");
        value.setRuleVersion(VERSION);
        return value;
    }

    private void addCondition(List<CapitalWatchCondition> values, CapitalFactorResult factors,
                              String code, String label, String operator, BigDecimal threshold, String unit) {
        Optional<CapitalFactorObservation> factor = factors.find(code);
        if (!factor.isPresent()) return;
        CapitalWatchCondition value = new CapitalWatchCondition();
        value.setId("watch:" + code);
        value.setLabel(label);
        value.setFactorRef(factor.get().factorRef());
        value.setOperator(operator);
        value.setThreshold(threshold);
        value.setUnit(unit);
        values.add(value);
    }
}
