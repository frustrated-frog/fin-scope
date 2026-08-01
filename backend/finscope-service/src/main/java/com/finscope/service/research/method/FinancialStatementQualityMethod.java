package com.finscope.service.research.method;

import com.finscope.service.research.mission.ResearchPlanningInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Order(10)
public class FinancialStatementQualityMethod implements ResearchMethod {
    private static final ResearchMethodDefinition DEFINITION = new ResearchMethodDefinition(
            "FINANCIAL_STATEMENT_QUALITY", "财报质量分析",
            "检查增长、盈利、现金流、资产质量和资本纪律，并主动寻找利润质量反证",
            Arrays.asList("增长由收入、毛利率还是费用变化驱动？", "利润是否被经营现金流支持？",
                    "应收、存货和减值是否与收入利润背离？"),
            Arrays.asList("多期利润表", "资产负债表", "现金流量表", "财报附注与审计意见"),
            Arrays.asList("同比与环比趋势", "经营现金流与净利润匹配度", "杜邦拆解", "同行财务指标对比"),
            Arrays.asList("非经常性损益对利润增长的贡献", "应收和存货增速是否高于收入增速",
                    "资本化、减值或会计政策变化是否改善表面利润"),
            Arrays.asList("覆盖利润、现金流和资产质量", "至少一项反方检查有明确结果",
                    "所有计算来自服务端且可追溯到报告期"),
            Arrays.asList("PRIMARY", "SUPPORT", "COUNTER", "ASSESS"));

    @Override
    public ResearchMethodDefinition definition() { return DEFINITION; }

    @Override
    public boolean supports(ResearchPlanningInput input) {
        if (!company(input)) return false;
        String text = text(input);
        return containsAny(text, "财报", "业绩", "利润", "盈利", "现金流", "营收", "毛利", "资产负债",
                "financial", "earnings", "cash flow", "revenue", "margin");
    }

    private boolean company(ResearchPlanningInput input) {
        String type = input == null || input.getSubjectType() == null ? "" : input.getSubjectType().trim();
        return "STOCK".equalsIgnoreCase(type) || "COMPANY".equalsIgnoreCase(type)
                || "EQUITY".equalsIgnoreCase(type);
    }

    private String text(ResearchPlanningInput input) {
        return input == null ? "" : ((input.getQuestion() == null ? "" : input.getQuestion()) + " "
                + (input.getSubjectName() == null ? "" : input.getSubjectName())).toLowerCase();
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) if (value.contains(keyword.toLowerCase())) return true;
        return false;
    }
}
