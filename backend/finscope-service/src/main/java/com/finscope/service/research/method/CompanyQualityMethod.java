package com.finscope.service.research.method;

import com.finscope.service.research.mission.ResearchPlanningInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Order(20)
public class CompanyQualityMethod implements ResearchMethod {
    private static final ResearchMethodDefinition DEFINITION = new ResearchMethodDefinition(
            "COMPANY_QUALITY", "公司质量判断",
            "从商业模式、竞争地位、治理、资本配置、成长持续性和失效条件判断公司质量",
            Arrays.asList("公司如何创造收入和现金流？", "竞争壁垒是否可验证且可持续？",
                    "管理层如何配置资本？", "哪些变化会使公司质量判断失效？"),
            Arrays.asList("公司公告与年度报告", "业务与客户结构", "竞争对手和行业资料", "治理与资本配置记录"),
            Arrays.asList("多期盈利与资本回报趋势", "客户产品集中度", "同行经营指标对比"),
            Arrays.asList("替代技术和竞争对手改善", "客户或供应商集中风险", "管理层治理和资本配置失误"),
            Arrays.asList("商业模式、壁垒和治理均有证据", "至少两个独立来源交叉验证",
                    "给出可观察的风险与失效条件"),
            Arrays.asList("PRIMARY", "SUPPORT", "COUNTER", "ASSESS"));

    @Override
    public ResearchMethodDefinition definition() { return DEFINITION; }

    @Override
    public boolean supports(ResearchPlanningInput input) {
        String type = input == null || input.getSubjectType() == null ? "" : input.getSubjectType().trim();
        return "STOCK".equalsIgnoreCase(type) || "COMPANY".equalsIgnoreCase(type)
                || "EQUITY".equalsIgnoreCase(type);
    }
}
