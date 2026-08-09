package com.finscope.service.learningcard;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class StockLearningFramework {
    public static final String CODE = "LIUJIE_BUYSIDE_RESEARCH_V1";
    private static final List<String> DIMENSIONS = Collections.unmodifiableList(Arrays.asList(
            "SPACE", "PROFIT_MODEL", "COMPETITION", "GOVERNANCE", "VALUATION", "COUNTER_CASE"));
    private static final List<String> FORBIDDEN = Arrays.asList("建议买入", "建议卖出", "买入", "卖出", "建仓", "清仓", "加仓", "减仓", "仓位", "目标价", "目标价格", "止盈", "止损", "强烈买入");
    private StockLearningFramework() { }
    public static List<String> dimensions() { return DIMENSIONS; }
    public static boolean isAllowedText(String value) {
        if (value == null) return true;
        for (String token : FORBIDDEN) if (value.contains(token)) return false;
        return true;
    }
}
