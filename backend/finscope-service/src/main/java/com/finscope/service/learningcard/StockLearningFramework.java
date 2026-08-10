package com.finscope.service.learningcard;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StockLearningFramework {
    public static final String CODE = "LIUJIE_BUYSIDE_RESEARCH_V1";
    private static final List<String> DIMENSIONS = Collections.unmodifiableList(Arrays.asList(
            "SPACE", "PROFIT_MODEL", "COMPETITION", "GOVERNANCE", "VALUATION", "COUNTER_CASE"));
    private static final List<String> FORBIDDEN = Arrays.asList(
            "建议买入", "建议卖出", "建议持有", "买入", "卖出", "建仓", "清仓", "加仓", "减仓", "仓位",
            "目标价", "目标价格", "目标位", "止盈", "止损", "强烈买入", "做多", "做空", "可以买", "可以卖",
            "年化收益", "保证收益", "收益承诺", "操作建议");
    private static final Map<String, String> QUERIES = queries();
    private static final Map<String, StockLearningDimensionSchema> SCHEMAS = schemas();
    private StockLearningFramework() { }
    public static List<String> dimensions() { return DIMENSIONS; }
    public static StockLearningDimensionSchema schemaFor(String dimension) {
        StockLearningDimensionSchema schema = SCHEMAS.get(dimension);
        if (schema == null) {
            throw new IllegalArgumentException("未知学习维度：" + dimension);
        }
        return schema;
    }
    public static String queryFor(String dimension, String companyName, String companyCode) {
        String suffix = QUERIES.get(dimension);
        if (suffix == null) throw new IllegalArgumentException("未知学习维度：" + dimension);
        return companyName + " " + companyCode + " " + suffix;
    }
    public static boolean isAllowedText(String value) {
        if (value == null) return true;
        for (String token : FORBIDDEN) if (value.contains(token)) return false;
        return true;
    }
    private static Map<String, String> queries() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("SPACE", "行业空间 产品 客户 产能 市场规模 公司公告");
        values.put("PROFIT_MODEL", "主营业务 收入 利润 现金流 年报");
        values.put("COMPETITION", "竞争格局 市场份额 主要对手 竞争优势 风险");
        values.put("GOVERNANCE", "公司治理 管理层 股权 激励 资本配置 公告");
        values.put("VALUATION", "估值 市盈率 市净率 市场预期 业绩 风险");
        values.put("COUNTER_CASE", "风险 反方 业绩下滑 竞争替代 治理风险");
        return Collections.unmodifiableMap(values);
    }

    private static Map<String, StockLearningDimensionSchema> schemas() {
        Map<String, StockLearningDimensionSchema> values = new LinkedHashMap<String, StockLearningDimensionSchema>();
        values.put("SPACE", schema("SPACE", "成长空间",
                definitions("business_map", "当前业务版图", "growth_drivers", "增量引擎",
                        "capture_capacity", "公司承接能力", "milestones", "兑现路径", "constraints", "增长约束"),
                definitions("market_ceiling", "行业天花板")));
        values.put("PROFIT_MODEL", schema("PROFIT_MODEL", "盈利质量",
                definitions("revenue_engine", "收入引擎", "profit_engine", "利润引擎",
                        "capital_efficiency", "资本效率", "cash_quality", "现金质量",
                        "earnings_elasticity", "盈利弹性"),
                definitions("unit_economics", "单位经济性", "quality_trend", "盈利质量趋势")));
        values.put("COMPETITION", schema("COMPETITION", "竞争位置",
                definitions("industry_structure", "行业格局", "company_position", "公司位置",
                        "moat", "护城河", "bargaining_power", "议价权", "winning_factors", "胜负手"),
                definitions("key_competitors", "核心对手", "competition_trend", "竞争趋势")));
        values.put("GOVERNANCE", schema("GOVERNANCE", "治理风险",
                definitions("control", "控制权", "incentive_alignment", "利益绑定",
                        "capital_allocation", "资本配置", "controlling_holder_risk", "大股东风险",
                        "disclosure_quality", "财务与披露", "delivery_record", "历史兑现"),
                definitions("shareholder_friendliness", "股东友好度")));
        values.put("VALUATION", schema("VALUATION", "市场预期",
                definitions("valuation_snapshot", "当前估值坐标", "implied_expectations", "隐含预期",
                        "expectation_feasibility", "预期可实现性", "expectation_gap", "预期差"),
                definitions("historical_position", "历史位置", "peer_comparison", "同行比较",
                        "catalysts", "关键催化剂")));
        values.put("COUNTER_CASE", schema("COUNTER_CASE", "证伪压力",
                definitions("core_assumptions", "核心投资假设", "counter_evidence", "逐条反证",
                        "falsification_conditions", "证伪条件", "stress_scenarios", "压力情景",
                        "leading_risk_indicators", "领先风险指标", "validation_status", "当前验证状态"),
                Collections.<StockLearningDimensionSchema.SectionDefinition>emptyList()));
        return Collections.unmodifiableMap(values);
    }

    private static StockLearningDimensionSchema schema(String code, String ratingLabel,
                                                        List<StockLearningDimensionSchema.SectionDefinition> required,
                                                        List<StockLearningDimensionSchema.SectionDefinition> optional) {
        return new StockLearningDimensionSchema(code, ratingLabel, required, optional);
    }

    private static List<StockLearningDimensionSchema.SectionDefinition> definitions(String... values) {
        List<StockLearningDimensionSchema.SectionDefinition> definitions =
                new java.util.ArrayList<StockLearningDimensionSchema.SectionDefinition>();
        for (int index = 0; index < values.length; index += 2) {
            definitions.add(StockLearningDimensionSchema.section(values[index], values[index + 1]));
        }
        return definitions;
    }
}
