package com.finscope.service.research.report;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchReportBlueprintValidatorTest {
    private final ResearchReportBlueprintValidator validator = new ResearchReportBlueprintValidator();

    @Test
    void rejectsBlueprintThatLeavesClaimsUngroundedAndIgnoresCounterEvidence() {
        ResearchReportBlueprint blueprint = blueprint();
        for (ResearchReportBlueprint.KeyInsight item : blueprint.getKeyInsights()) item.setEvidenceRefs(new ArrayList<String>());
        for (ResearchReportBlueprint.SubQuestion item : blueprint.getSubQuestions()) item.setEvidenceRefs(new ArrayList<String>());
        for (ResearchReportBlueprint.ArgumentChain item : blueprint.getArgumentChains()) item.setEvidenceRefs(new ArrayList<String>());
        blueprint.getStrongestCounterargument().setEvidenceRefs(Arrays.asList("E1"));

        List<String> issues = validator.validate(blueprint, dossier());

        assertTrue(issues.contains("UNGROUNDED_KEY_INSIGHT"));
        assertTrue(issues.contains("UNGROUNDED_SUBQUESTION"));
        assertTrue(issues.contains("UNGROUNDED_ARGUMENT_CHAIN"));
        assertTrue(issues.contains("COUNTER_EVIDENCE_NOT_USED"));
        assertTrue(issues.contains("INSUFFICIENT_BLUEPRINT_CITATION_COVERAGE"));
    }

    @Test
    void acceptsBlueprintWhoseClaimsCoverSupportingAndCounterEvidence() {
        List<String> issues = validator.validate(blueprint(), dossier());

        assertFalse(issues.stream().anyMatch(item -> item.startsWith("UNGROUNDED_")));
        assertFalse(issues.contains("COUNTER_EVIDENCE_NOT_USED"));
        assertFalse(issues.contains("INSUFFICIENT_BLUEPRINT_CITATION_COVERAGE"));
    }

    private ResearchReportBlueprint blueprint() {
        ResearchReportBlueprint result = new ResearchReportBlueprint();
        result.setDirectAnswer("当前证据支持阶段性判断，但仍需跟踪反方条件。");
        result.setKeyInsights(Arrays.asList(insight("认识一", "E1", "E2"),
                insight("认识二", "E3", "E4"), insight("认识三", "E5", "E6")));
        result.setSubQuestions(Arrays.asList(subQuestion("question_one", "E1", "E2"),
                subQuestion("question_two", "E3", "E4"), subQuestion("question_three", "E5", "E6")));
        result.setArgumentChains(Arrays.asList(chain("E1", "E3"), chain("E4", "E5")));
        ResearchReportBlueprint.Counterargument counter = new ResearchReportBlueprint.Counterargument();
        counter.setClaim("反方解释");
        counter.setEvidenceRefs(Arrays.asList("E6"));
        counter.setResponse("当前证据不足以使反方成为主导解释");
        counter.setBecomesDominantWhen(Arrays.asList("后续指标持续恶化"));
        result.setStrongestCounterargument(counter);
        return result;
    }

    private ResearchReportBlueprint.KeyInsight insight(String finding, String... refs) {
        ResearchReportBlueprint.KeyInsight item = new ResearchReportBlueprint.KeyInsight();
        item.setFinding(finding);
        item.setMeaning("形成可验证认识");
        item.setEvidenceRefs(Arrays.asList(refs));
        return item;
    }

    private ResearchReportBlueprint.SubQuestion subQuestion(String key, String... refs) {
        ResearchReportBlueprint.SubQuestion item = new ResearchReportBlueprint.SubQuestion();
        item.setKey(key);
        item.setQuestion("子问题");
        item.setAnswer("阶段性回答");
        item.setImpact("影响总判断");
        item.setEvidenceRefs(Arrays.asList(refs));
        return item;
    }

    private ResearchReportBlueprint.ArgumentChain chain(String... refs) {
        ResearchReportBlueprint.ArgumentChain item = new ResearchReportBlueprint.ArgumentChain();
        item.setFact("可验证事实");
        item.setInference("推理过程");
        item.setJudgment("阶段性判断");
        item.setAlternativeExplanation("替代解释");
        item.setEvidenceRefs(Arrays.asList(refs));
        return item;
    }

    private List<ResearchEvidenceDossier> dossier() {
        List<ResearchEvidenceDossier> result = new ArrayList<ResearchEvidenceDossier>();
        for (int index = 1; index <= 6; index++) {
            result.add(new ResearchEvidenceDossier("E" + index, (long) index, null, "source-" + index,
                    "来源" + index, "MEDIA", "证据" + index, LocalDateTime.of(2026, 7, 29, 10, 0),
                    "https://example.com/" + index, "事实" + index,
                    index == 6 ? "COUNTER" : "SUPPORT", 90));
        }
        return result;
    }
}
