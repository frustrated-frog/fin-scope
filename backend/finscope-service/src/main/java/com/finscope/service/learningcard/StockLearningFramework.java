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
    private StockLearningFramework() { }
    public static List<String> dimensions() { return DIMENSIONS; }
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
}
