package com.finscope.service.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.domain.factorresearch.FactorIdentity;
import com.finscope.domain.factorresearch.FactorLifecycleStatus;
import com.finscope.domain.factorresearch.ResearchFactorDefinition;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned, human-readable research definitions. A catalog entry documents a
 * hypothesis and its limits; it is not evidence that the factor is validated.
 */
@Service
public class ResearchFactorCatalog {
    private final Map<FactorIdentity, ResearchFactorDefinition> definitions;

    public ResearchFactorCatalog() {
        Map<FactorIdentity, ResearchFactorDefinition> values =
                new LinkedHashMap<FactorIdentity, ResearchFactorDefinition>();

        add(values, marketFactor("MOMENTUM_20D", "20日动量", "动量", "POSITIVE_HYPOTHESIS",
                "比较当前复权收盘价与20个交易日前复权收盘价，观察中短期价格趋势强弱",
                "若市场信息被逐步吸收，过去20个交易日相对更强的股票，未来短期收益排序可能仍偏强",
                "价格对新信息的反应可能分散在多个交易日，趋势跟随和投资者反应不足可能形成短期延续",
                "20日收益不等于未来还会涨；趋势反转、拥挤交易和极端行情会使动量快速失效，且该实现未做行业或规模中性化",
                Arrays.asList("adjustedClose", "tradeDate"),
                "adjustedClose[t] / adjustedClose[t-20] - 1", "price-return-v1"));
        add(values, marketFactor("MOMENTUM_60D", "60日动量", "动量", "POSITIVE_HYPOTHESIS",
                "比较当前复权收盘价与60个交易日前复权收盘价，观察更长窗口的价格趋势",
                "若中期趋势具有延续性，过去60个交易日收益排名较高的股票，未来短期收益排名可能仍偏高",
                "较长窗口降低单日噪声，但也会混合多个市场阶段，反映的是中期趋势而非即时催化",
                "60日窗口会滞后于拐点；趋势崩溃、风格切换和停牌样本会扭曲比较，不能把历史涨幅当作安全边际",
                Arrays.asList("adjustedClose", "tradeDate"),
                "adjustedClose[t] / adjustedClose[t-60] - 1", "price-return-v1"));
        add(values, marketFactor("REVERSAL_5D", "5日反转", "反转", "POSITIVE_HYPOTHESIS",
                "取过去5个交易日复权收益的相反数，用高值表示近期相对跌幅更大",
                "若短期价格变化包含流动性冲击或过度反应，过去5日相对较弱的股票可能出现均值回归",
                "短期交易压力消退、被动资金再平衡或投资者过度反应修正，可能产生短周期反转",
                "负收益的相反数不代表低估；基本面恶化和持续趋势中下跌可能继续，5日窗口也容易受到涨跌停影响",
                Arrays.asList("adjustedClose", "tradeDate"),
                "-(adjustedClose[t] / adjustedClose[t-5] - 1)", "price-return-v1"));
        add(values, marketFactor("VOLATILITY_20D", "20日低波", "波动", "POSITIVE_HYPOTHESIS",
                "计算最近20个交易日日收益的样本标准差并取相反数，用高值表示历史波动较低",
                "若高风险股票没有获得足够风险补偿，历史波动较低的股票在风险调整后的表现可能更稳定",
                "投资者偏好高波动标的、杠杆约束和防御需求，可能使低波动股票获得相对定价优势",
                "历史低波不保证未来低波；该值未分解市场贝塔和行业暴露，极端行情、停牌或价格跳空会改变风险含义",
                Arrays.asList("adjustedClose", "tradeDate"),
                "-sampleStd(dailyAdjustedReturn, 20)", "price-volatility-v1"));
        add(values, marketFactor("AVG_AMOUNT_20D", "20日成交额", "流动性", "POSITIVE_HYPOTHESIS",
                "对最近20个交易日平均成交额取自然对数，描述标的日常可成交规模",
                "更高的常态成交额通常意味着更好的交易承载能力，但是否带来超额收益必须单独验证",
                "成交额是价格与成交量共同形成的容量代理，取对数可降低极大市值股票对截面的机械支配",
                "这是流动性与容量暴露，不是资金净流入或买入意愿；高成交额也可能来自剧烈分歧，20日均值会掩盖日内变化",
                Arrays.asList("amount", "tradeDate"),
                "ln(max(1, mean(amount, 20)))", "price-liquidity-v1"));
        add(values, marketFactor("TURNOVER_PROXY_20D", "20日换手代理", "流动性", "NEGATIVE_HYPOTHESIS",
                "用最近20日成交量的变异系数衡量成交活跃度是否稳定，低值表示成交量相对均值更平稳",
                "若异常活跃和交易拥挤包含更高冲击成本，成交量相对更稳定的股票可能具有更好的可交易性",
                "标准差除以均值把成交量波动按自身常态归一化，便于比较不同成交规模的标的",
                "当前公式只使用成交量，不是真实换手率；低值也可能来自长期不活跃，必须与成交额和可交易性门禁一起判断",
                Arrays.asList("volume", "tradeDate"),
                "std(volume, 20) / mean(volume, 20)", "volume-variation-v1"));

        add(values, fundamentalFactor("LOG_MARKET_CAP", "对数总市值", "规模", "NEGATIVE_HYPOTHESIS",
                "对当时可获得的总市值取自然对数，描述公司的规模暴露",
                "若小规模公司承担更高风险或受到更少关注，较低市值股票可能存在规模补偿，但结论依赖市场阶段",
                "对数变换压缩极端市值差异，使规模在横截面比较中更平滑，也常作为风险控制变量",
                "低市值不等于便宜，可能伴随流动性、退市和治理风险；总市值字段的实际可得时间必须受快照约束",
                Arrays.asList("marketCap", "availableAt"), "ln(marketCap)", "fundamental-size-v1"));
        add(values, fundamentalFactor("EP", "盈利收益率", "价值", "POSITIVE_HYPOTHESIS",
                "用市盈率的倒数近似每一单位市值对应的会计盈利，数值越高通常表示估值越低",
                "在盈利口径可比且未失真的前提下，较高盈利收益率可能获得估值修复或价值风险补偿",
                "把价格倍数取倒数后更适合横截面排序，并可直观理解为价格对应的历史盈利比例",
                "盈利为负、一次性损益或极小市盈率会使倒数失真；1 / pe 不是债券收益率，也不代表盈利可分配",
                Arrays.asList("pe", "availableAt"), "1 / pe", "fundamental-value-v1"));
        add(values, fundamentalFactor("BP", "账面市值比", "价值", "POSITIVE_HYPOTHESIS",
                "用市净率的倒数表示每一单位市值对应的账面净资产",
                "若账面资产具有经济价值，较高账面市值比可能对应更高的价值补偿或估值修复空间",
                "净资产相对市场价格提供一种资产基础估值视角，常用于与盈利价值指标互相验证",
                "不同行业资产质量和会计口径差异很大；负净资产、商誉减值和金融资产重估会使 1 / pb 难以比较",
                Arrays.asList("pb", "availableAt"), "1 / pb", "fundamental-value-v1"));
        add(values, fundamentalFactor("ROE", "净资产收益率", "质量", "POSITIVE_HYPOTHESIS",
                "读取当时已披露的净资产收益率，观察公司使用股东资本创造会计利润的能力",
                "若盈利能力可以持续，较高 ROE 的公司可能具有更稳定的商业质量和长期复利能力",
                "ROE 将利润与股东投入资本联系起来，可辅助区分单纯规模增长和资本使用效率",
                "高 ROE 可能由高杠杆、低净资产或一次性收益造成；只读单期值不能判断持续性，且必须遵守披露时点",
                Arrays.asList("roe", "availableAt"), "roe", "fundamental-quality-v1"));
        add(values, fundamentalFactor("LOW_DEBT", "低负债", "质量", "POSITIVE_HYPOTHESIS",
                "对当时已披露的资产负债率取相反数，用高值表示账面杠杆相对较低",
                "在其他条件相近时，较低负债可能降低再融资和偿付压力，使公司在下行阶段更具韧性",
                "负号只是把排序方向统一为高值优先，经济含义仍然是资产负债率较低",
                "负号只是排序变换，-debtRatio 不代表负债越少一定越好；银行等行业不可直接横比，表外负债和期限结构也未被覆盖",
                Arrays.asList("debtRatio", "availableAt"), "-debtRatio", "fundamental-quality-v1"));
        add(values, fundamentalFactor("REVENUE_GROWTH", "营收增长", "成长", "POSITIVE_HYPOTHESIS",
                "读取当时已披露的营业收入同比增速，观察业务规模相对上年同期的扩张速度",
                "若收入增长可持续且没有以显著牺牲盈利质量为代价，较高增速可能反映更强的业务需求",
                "同比口径降低季节性影响，收入通常比利润更接近业务扩张的第一层结果",
                "高增速可能来自低基数、并购或价格上涨；收入增长不等于利润和现金流增长，披露滞后必须按时点处理",
                Arrays.asList("revenueGrowth", "availableAt"), "revenueGrowth", "fundamental-growth-v1"));
        add(values, fundamentalFactor("PROFIT_GROWTH", "利润增长", "成长", "POSITIVE_HYPOTHESIS",
                "读取当时已披露的净利润同比增速，观察股东口径会计利润的扩张速度",
                "若利润增长来自可持续经营，较高增速可能反映盈利能力改善并获得市场重新定价",
                "净利润增速同时受收入、毛利率、费用和非经常性项目影响，是经营结果的综合变化",
                "高增速可能来自低基数、一次性损益或会计调整；利润为负时同比含义尤其不稳定，不能脱离现金流判断",
                Arrays.asList("profitGrowth", "availableAt"), "profitGrowth", "fundamental-growth-v1"));

        ResearchFactorDefinition mainFlowShare = ResearchFactorDefinition.builder()
                .identity(CapitalFlowFactorProvider.MAIN_FLOW_SHARE)
                .name("主力流入强度")
                .category("资金行为")
                .frequency("DAILY")
                .expectedDirection("POSITIVE_HYPOTHESIS")
                .plainMeaning("主力净流入占当日成交额的比例")
                .hypothesis("在信息可得时间一致的前提下，较高的主力净流入强度可能对应更强的短期需求压力，但该关系必须经样本外检验后才能使用")
                .economicRationale("成交额归一化降低绝对资金规模和股票体量的机械影响，使同一交易日不同标的之间具有初步可比性")
                .interpretationBoundary("数据供应商的“主力”是按成交单特征划分的统计口径，不代表已识别真实交易主体；单日资金流噪声较高，可能受涨跌停、成交活跃度和行情状态影响；该因子当前仅为探索性研究假设，不证明因果关系，不构成投资建议")
                .requiredFields(Arrays.asList("datasetId", "tradeDate", "instrumentCode",
                        "availableAt", "mainNetInflow", "amount", "qualityStatus"))
                .availableAtRule("只使用已冻结数据；availableAt 严格等于源快照 retrievedAt，且不得晚于策略信号对应的下一交易日开盘时间")
                .missingPolicy("任一必需字段缺失、amount 非正、质量非 COMPLETE 或数据尚不可见时返回 MISSING_INPUT，不做零值填充")
                .calculationKey("mainNetInflow / amount，保留 10 位小数并采用 HALF_UP")
                .calculationVersion("capital-flow-share-v1")
                .sourceType("FROZEN_CAPITAL_FLOW")
                .sourceRef("market_capital_flow_snapshot.DAY_1 -> quant_capital_flow_daily")
                .evaluationPolicyCode("CROSS_SECTIONAL_FORWARD_RETURN")
                .evaluationPolicyVersion(FactorValidationPolicy.VERSION)
                .status(FactorLifecycleStatus.EXPLORATORY)
                .build();
        add(values, mainFlowShare);
        add(values, capitalShareFactor(CapitalFlowFactorProvider.SUPER_LARGE_FLOW_SHARE,
                "超大单流入强度", "超大单净流入占当日成交额的比例",
                "superLargeNetInflow / amount",
                Arrays.asList("superLargeNetInflow", "amount"),
                "该口径只表示供应商分类下的超大单成交净额，不能据此识别机构、主力或具体交易主体"));
        add(values, capitalShareFactor(CapitalFlowFactorProvider.BIG_ORDER_FLOW_SHARE,
                "大单合计流入强度", "超大单与大单净流入之和占当日成交额的比例",
                "(superLargeNetInflow + largeNetInflow) / amount",
                Arrays.asList("superLargeNetInflow", "largeNetInflow", "amount"),
                "这是两个订单规模桶的合计统计；任一桶缺失即拒绝计算，不把缺失值当作零，也不等同于真实机构净买入"));
        add(values, capitalWindowFactor(CapitalFlowFactorProvider.NORMALIZED_MAIN_FLOW_SUM_5D,
                "5日主力流入累计", "最近5个完整交易日的主力净流入强度之和",
                "sum(mainNetInflow / amount, 5 trading days)", 5,
                "累计值同时混合方向和持续时间，不能与绝对资金净额混用；缺一天就拒绝计算"));
        add(values, capitalWindowFactor(CapitalFlowFactorProvider.FLOW_PERSISTENCE_5D,
                "5日资金持续性", "最近5个完整交易日流入方向符号的平均值，范围为 -1 到 1",
                "mean(sign(mainNetInflow / amount), 5 trading days)", 5,
                "它只衡量方向是否连续，不衡量资金强度；连续微小流入也可能得到高值"));
        add(values, capitalWindowFactor(CapitalFlowFactorProvider.MAIN_FLOW_SHARE_ZSCORE_20D,
                "20日资金强度 Z 分数", "当前主力流入强度相对自身最近20个交易日常态的标准化偏离",
                "(share[t] - mean(share,20)) / populationStd(share,20)", 20,
                "这是标的自身时间序列异常度，不是机构身份识别；标准差为零或窗口不完整时拒绝计算"));
        add(values, capitalWindowFactor(CapitalFlowFactorProvider.PRICE_FLOW_DIVERGENCE_5D,
                "5日价格—资金背离", "将5日价格收益与同期归一化资金累计的乘积取反，正值表示方向相反",
                "-return(adjustedClose,5) * sum(mainNetInflow / amount,5)", 5,
                "正值只表示价格和资金统计方向相反，不自动等于反转机会；复权价格需6个交易日、资金需5个完整交易日"));
        this.definitions = Collections.unmodifiableMap(values);
    }

    private static ResearchFactorDefinition capitalShareFactor(FactorIdentity identity, String name,
                                                                 String meaning, String formula,
                                                                 List<String> sourceFields, String boundary) {
        List<String> fields = new java.util.ArrayList<String>(Arrays.asList(
                "datasetId", "tradeDate", "instrumentCode", "availableAt", "qualityStatus"));
        fields.addAll(sourceFields);
        return ResearchFactorDefinition.builder()
                .identity(identity)
                .name(name)
                .category("资金行为")
                .frequency("DAILY")
                .expectedDirection("POSITIVE_HYPOTHESIS")
                .plainMeaning(meaning)
                .hypothesis("在严格控制可得时间和成交规模后，该流入强度与下一交易日横截面收益可能同向；它仍是待检验的研究假设")
                .economicRationale("用成交额归一化可以减少股票体量对绝对净额的机械影响，并允许在同一交易日做初步横截面比较")
                .interpretationBoundary(boundary + "；单日数值噪声较高，当前仅为探索性因子，不构成投资建议")
                .requiredFields(fields)
                .availableAtRule("只使用冻结行真实 availableAt 不晚于本次信号 executionCutoff 的数据")
                .missingPolicy("质量非 COMPLETE、分母非正、必需订单桶缺失或尚未到可见时点时返回 MISSING_INPUT")
                .calculationKey(formula + "，保留 10 位小数并采用 HALF_UP")
                .calculationVersion("capital-flow-share-v1")
                .sourceType("FROZEN_CAPITAL_FLOW")
                .sourceRef("market_capital_flow_snapshot.DAY_1 -> quant_capital_flow_daily")
                .evaluationPolicyCode("CROSS_SECTIONAL_FORWARD_RETURN")
                .evaluationPolicyVersion(FactorValidationPolicy.VERSION)
                .status(FactorLifecycleStatus.EXPLORATORY)
                .build();
    }

    private static ResearchFactorDefinition capitalWindowFactor(FactorIdentity identity, String name,
                                                                  String meaning, String formula,
                                                                  int window, String boundary) {
        return ResearchFactorDefinition.builder().identity(identity).name(name).category("资金行为")
                .frequency("DAILY").expectedDirection("POSITIVE_HYPOTHESIS").plainMeaning(meaning)
                .hypothesis("若资金行为具有短期延续或与价格形成可解释的错位，该指标与下一交易日横截面收益可能同向；必须由数据检验")
                .economicRationale("先以每日成交额归一化，再在连续交易日窗口聚合，降低绝对资金规模和非交易日间隔造成的机械偏差")
                .interpretationBoundary(boundary + "；当前仅为探索性研究假设，不构成投资建议")
                .requiredFields(Arrays.asList("tradeDate", "availableAt", "qualityStatus", "mainNetInflow", "amount", "adjustedClose"))
                .availableAtRule("严格按行情交易日历取最近 " + window + " 个完整交易日；所有资金行 availableAt 必须不晚于 executionCutoff")
                .missingPolicy("窗口不足、任一天资金行缺失/迟到/质量异常、amount 非正时整项返回 MISSING_INPUT，不压缩窗口")
                .calculationKey(formula + "，结果保留10位小数")
                .calculationVersion("capital-window-v1").sourceType("FROZEN_CAPITAL_FLOW_AND_DAILY_BAR")
                .sourceRef("quant_capital_flow_daily + quant_daily_bar")
                .evaluationPolicyCode("CROSS_SECTIONAL_FORWARD_RETURN").evaluationPolicyVersion(FactorValidationPolicy.VERSION)
                .status(FactorLifecycleStatus.EXPLORATORY).build();
    }

    private static ResearchFactorDefinition marketFactor(String code, String name, String category,
                                                          String direction, String plainMeaning,
                                                          String hypothesis, String rationale,
                                                          String boundary, List<String> fields,
                                                          String formula, String calculationVersion) {
        return legacyFactor(code, name, category, direction, plainMeaning, hypothesis, rationale,
                boundary, fields, "每日收盘后形成；只使用信号日及以前的已冻结复权行情",
                "窗口不足、价格/成交字段缺失或分母非正时返回缺失，不以零填充",
                formula, calculationVersion, "FROZEN_DAILY_BAR",
                "quant_daily_bar -> FactorCalculator");
    }

    private static ResearchFactorDefinition fundamentalFactor(String code, String name, String category,
                                                               String direction, String plainMeaning,
                                                               String hypothesis, String rationale,
                                                               String boundary, List<String> fields,
                                                               String formula, String calculationVersion) {
        return legacyFactor(code, name, category, direction, plainMeaning, hypothesis, rationale,
                boundary, fields, "只读取 availableAt 不晚于信号形成时点的最新冻结基本面快照",
                "快照不可见、字段缺失或对数/倒数定义域不成立时返回缺失，不前向读取未来披露",
                formula, calculationVersion, "POINT_IN_TIME_FUNDAMENTAL",
                "quant_fundamental_snapshot -> FactorCalculator");
    }

    private static ResearchFactorDefinition legacyFactor(String code, String name, String category,
                                                          String direction, String plainMeaning,
                                                          String hypothesis, String rationale,
                                                          String boundary, List<String> fields,
                                                          String availableAtRule, String missingPolicy,
                                                          String formula, String calculationVersion,
                                                          String sourceType, String sourceRef) {
        return ResearchFactorDefinition.builder()
                .identity(new FactorIdentity("quant", code, "1.0.0"))
                .name(name)
                .category(category)
                .frequency("DAILY")
                .expectedDirection(direction)
                .plainMeaning(plainMeaning)
                .hypothesis(hypothesis)
                .economicRationale(rationale)
                .interpretationBoundary(boundary)
                .requiredFields(fields)
                .availableAtRule(availableAtRule)
                .missingPolicy(missingPolicy)
                .calculationKey(formula)
                .calculationVersion(calculationVersion)
                .sourceType(sourceType)
                .sourceRef(sourceRef)
                .evaluationPolicyCode("CROSS_SECTIONAL_FORWARD_RETURN")
                .evaluationPolicyVersion(FactorValidationPolicy.VERSION)
                .status(FactorLifecycleStatus.CALCULATION_VERIFIED)
                .build();
    }

    private static void add(Map<FactorIdentity, ResearchFactorDefinition> values,
                            ResearchFactorDefinition definition) {
        if (values.put(definition.getIdentity(), definition) != null) {
            throw new IllegalStateException("duplicate factor identity: " + definition.getIdentity());
        }
    }

    public List<ResearchFactorDefinition> list() {
        return Collections.unmodifiableList(
                new java.util.ArrayList<ResearchFactorDefinition>(definitions.values()));
    }

    public ResearchFactorDefinition get(String namespace, String code, String version) {
        final FactorIdentity identity;
        try {
            identity = new FactorIdentity(namespace, code, version);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "因子命名空间、编码和版本不能为空");
        }
        ResearchFactorDefinition value = definitions.get(identity);
        if (value == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "研究因子版本不存在：" + identity);
        }
        return value;
    }
}
