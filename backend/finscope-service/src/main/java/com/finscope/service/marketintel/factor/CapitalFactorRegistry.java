package com.finscope.service.marketintel.factor;

import com.finscope.domain.marketintel.CapitalFactorDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CapitalFactorRegistry {
    public static final String VERSION = "capital-factor-v1";
    private final List<CapitalFactorDefinition> definitions;

    public CapitalFactorRegistry() {
        List<CapitalFactorDefinition> values = Arrays.asList(
                factor("AMOUNT_RATIO_5D", "5日量能比", "VOLUME", "REF(amount)/MEAN(amount,5)", "5d", 2, "intervalTradeAmount"),
                factor("AMOUNT_RATIO_20D", "20日量能比", "VOLUME", "REF(amount)/MEAN(amount,20)", "20d", 2, "intervalTradeAmount"),
                factor("AMOUNT_ZSCORE_20D", "20日成交额异常", "VOLUME", "ZSCORE(amount,20)", "20d", 5, "intervalTradeAmount"),
                factor("TURNOVER_PERCENTILE", "换手率历史分位", "TURNOVER", "TS_RANK(turnover,20)", "20d", 5, "turnoverRate"),
                factor("VOLUME_RATIO_LATEST", "最新量比", "VOLUME", "REF(volumeRatio)", "latest", 1, "volumeRatio"),
                factor("MAIN_FLOW_SHARE", "主力净额占比", "FLOW", "mainNet/amount", "latest", 1, "mainNetInflow", "intervalTradeAmount"),
                factor("MAIN_FLOW_SUM_5D", "5日主力净额", "MULTI_PERIOD", "SUM(mainNet,5)", "5d", 1, "mainNetInflow"),
                factor("MAIN_FLOW_SUM_10D", "10日主力净额", "MULTI_PERIOD", "SUM(mainNet,10)", "10d", 1, "mainNetInflow"),
                factor("MAIN_FLOW_SUM_20D", "20日主力净额", "MULTI_PERIOD", "SUM(mainNet,20)", "20d", 1, "mainNetInflow"),
                factor("MAIN_FLOW_STREAK", "主力连续方向", "MULTI_PERIOD", "SIGNED_STREAK(mainNet)", "daily", 1, "mainNetInflow"),
                factor("MAIN_FLOW_SLOPE_5D", "5日资金斜率", "MULTI_PERIOD", "SLOPE(mainNet,5)", "5d", 2, "mainNetInflow"),
                factor("MAIN_FLOW_ZSCORE_20D", "20日资金异常", "FLOW", "ZSCORE(mainNet,20)", "20d", 5, "mainNetInflow"),
                factor("BIG_ORDER_NET", "大单净额", "ORDER_STRUCTURE", "superLargeNet+largeNet", "latest", 1, "superLargeNetInflow", "largeNetInflow"),
                factor("SMALL_MID_ORDER_NET", "中小单净额", "ORDER_STRUCTURE", "mediumNet+smallNet", "latest", 1, "mediumNetInflow", "smallNetInflow"),
                factor("BIG_SMALL_DIVERGENCE", "大单与中小单背离", "ORDER_STRUCTURE", "SIGN(bigNet)!=SIGN(smallMidNet)", "latest", 1, "superLargeNetInflow", "largeNetInflow", "mediumNetInflow", "smallNetInflow"),
                factor("SUPER_LARGE_CONTRIBUTION", "超大单贡献", "ORDER_STRUCTURE", "superLargeNet/mainNet", "latest", 1, "superLargeNetInflow", "mainNetInflow"),
                factor("PRICE_FLOW_ALIGNMENT", "价格资金一致性", "FLOW", "SIGN(DELTA(price))=SIGN(mainNet)", "2d", 2, "price", "mainNetInflow"),
                factor("PRICE_VOLUME_ALIGNMENT", "价格量能一致性", "VOLUME", "SIGN(DELTA(price))=SIGN(DELTA(amount))", "2d", 2, "price", "intervalTradeAmount"),
                factor("PRICE_VOLUME_FLOW_REGIME", "价量资金状态", "MULTI_PERIOD", "REGIME(price,amount,mainNet)", "2d", 2, "price", "intervalTradeAmount", "mainNetInflow"),
                factor("INTRADAY_FLOW_REVERSALS", "日内资金反转", "INTRADAY", "COUNT(SIGN_CHANGE(mainNet))", "session", 3, "mainNetInflow"),
                factor("INTRADAY_FLOW_ACCELERATION", "日内资金加速度", "INTRADAY", "SLOPE(mainNet,last3)", "last3", 3, "mainNetInflow"),
                factor("LATE_SESSION_FLOW_SHARE", "尾盘资金占比", "INTRADAY", "SUM(lateMainNet)/SUM(ABS(mainNet))", "session", 4, "mainNetInflow"),
                factor("PEAK_INFLOW_BUCKET", "最大净流入时段", "INTRADAY", "ARGMAX(mainNet)", "session", 1, "mainNetInflow"),
                factor("PEAK_OUTFLOW_BUCKET", "最大净流出时段", "INTRADAY", "ARGMIN(mainNet)", "session", 1, "mainNetInflow")
        );
        validate(values);
        definitions = Collections.unmodifiableList(new ArrayList<CapitalFactorDefinition>(values));
    }

    public List<CapitalFactorDefinition> published() {
        return Collections.unmodifiableList(definitions.stream()
                .filter(item -> item.getAdmissionStatus() == CapitalFactorDefinition.AdmissionStatus.PUBLISHED)
                .collect(Collectors.toList()));
    }

    public Optional<CapitalFactorDefinition> find(String code) {
        return published().stream().filter(item -> item.getCode().equals(code)).findFirst();
    }

    private CapitalFactorDefinition factor(String code, String name, String category, String formula,
                                           String window, int samples, String... fields) {
        boolean externalPattern = code.startsWith("AMOUNT_") || code.contains("ZSCORE") || code.contains("SLOPE");
        return CapitalFactorDefinition.builder(code, name)
                .category(category)
                .description(name + "的确定性资金行为观测")
                .expressionKind(formula.startsWith("REGIME") || formula.startsWith("COUNT") || formula.startsWith("ARG")
                        ? CapitalFactorDefinition.ExpressionKind.CALCULATOR
                        : CapitalFactorDefinition.ExpressionKind.DECLARATIVE)
                .canonicalFormula(formula)
                .calculationKey(code)
                .requiredFields(Arrays.asList(fields))
                .window(window)
                .minimumSamples(samples)
                .sourceType(externalPattern ? CapitalFactorDefinition.SourceType.QLIB : CapitalFactorDefinition.SourceType.INTERNAL)
                .sourceRef(externalPattern ? "Qlib Alpha158/Alpha360 窗口特征范式" : "FinScope 资金行为领域规则")
                .adaptationType(externalPattern ? CapitalFactorDefinition.AdaptationType.ADAPTED : CapitalFactorDefinition.AdaptationType.ORIGINAL)
                .calculationVersion(VERSION)
                .evaluationStatus(CapitalFactorDefinition.EvaluationStatus.UNTESTED)
                .admissionStatus(CapitalFactorDefinition.AdmissionStatus.PUBLISHED)
                .interpretationBoundary("仅描述公开行情与资金口径下的当前状态，不代表收益预测或真实机构身份。")
                .build();
    }

    private void validate(List<CapitalFactorDefinition> values) {
        Set<String> codes = new HashSet<String>();
        Set<String> keys = new HashSet<String>();
        for (CapitalFactorDefinition value : values) {
            if (!codes.add(value.getCode())) throw new IllegalStateException("duplicate factor code " + value.getCode());
            if (!keys.add(value.getCalculationKey())) throw new IllegalStateException("duplicate calculation key " + value.getCalculationKey());
            if (value.getAdmissionStatus() == CapitalFactorDefinition.AdmissionStatus.PUBLISHED
                    && value.getInterpretationBoundary().trim().isEmpty()) {
                throw new IllegalStateException("published factor missing interpretation boundary " + value.getCode());
            }
        }
    }
}
