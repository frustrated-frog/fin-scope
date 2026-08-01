package com.finscope.service.research.method;

import com.finscope.service.research.mission.ResearchPlanningInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchMethodRegistryTest {
    private final ResearchMethodRegistry registry = ResearchMethodRegistry.defaults();

    @Test
    void recommendsFinancialAndCompanyQualityForFinancialStatementQuestion() {
        ResearchPlanningInput input = input("STOCK", "宁德时代", "最新财报是否说明盈利质量改善？");

        List<String> codes = registry.recommend(input).stream()
                .map(ResearchMethodDefinition::getCode)
                .collect(Collectors.toList());

        assertEquals(java.util.Arrays.asList("FINANCIAL_STATEMENT_QUALITY", "COMPANY_QUALITY"), codes);
    }

    @Test
    void recommendsOnlyCompanyQualityForGeneralCompanyQuestion() {
        ResearchPlanningInput input = input("STOCK", "宁德时代", "这家公司的竞争壁垒和商业模式怎么样？");

        List<String> codes = registry.recommend(input).stream()
                .map(ResearchMethodDefinition::getCode)
                .collect(Collectors.toList());

        assertEquals(java.util.Collections.singletonList("COMPANY_QUALITY"), codes);
    }

    @Test
    void doesNotApplyCompanyMethodsToThemeResearch() {
        assertTrue(registry.recommend(input("THEME", "AI算力", "资本开支能否持续？")).isEmpty());
    }

    @Test
    void financialMethodCarriesDeterministicEvidenceAndCounterContracts() {
        ResearchMethodDefinition method = registry.required("FINANCIAL_STATEMENT_QUALITY");

        assertTrue(method.getRequiredEvidence().contains("现金流量表"));
        assertTrue(method.getRequiredCalculations().contains("经营现金流与净利润匹配度"));
        assertTrue(method.getCounterChecks().contains("非经常性损益对利润增长的贡献"));
        assertFalse(method.getCompletionCriteria().isEmpty());
        assertEquals(java.util.Arrays.asList("PRIMARY", "SUPPORT", "COUNTER", "ASSESS"),
                method.getRequiredIntents());
    }

    private ResearchPlanningInput input(String type, String name, String question) {
        ResearchPlanningInput input = new ResearchPlanningInput();
        input.setSubjectType(type);
        input.setSubjectName(name);
        input.setQuestion(question);
        return input;
    }
}
