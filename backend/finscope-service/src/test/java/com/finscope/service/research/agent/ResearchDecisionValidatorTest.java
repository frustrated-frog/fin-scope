package com.finscope.service.research.agent;

import com.finscope.domain.research.agent.ResearchAgentDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchDecisionValidatorTest {
    private ResearchDecisionValidator validator;
    private ResearchDecisionContext context;

    @BeforeEach
    void setUp() {
        validator = new ResearchDecisionValidator();
        context = new ResearchDecisionContext();
        context.setResearchRunId(7L);
        context.setNextIteration(3);
        context.setRemainingActions(4);
        context.setAttemptedFingerprints(Collections.<String>emptyList());
    }

    @Test
    void validatesTypedToolCallAndBuildsStableFingerprint() {
        ResearchAgentDecision decision = validator.validate(searchDraft(), context, "MODEL");

        assertEquals(7L, decision.getResearchRunId());
        assertEquals(3, decision.getIteration());
        assertEquals("public_news_search", decision.getToolCode());
        assertEquals("MODEL", decision.getDecisionMode());
        assertEquals("PROPOSED", decision.getStatus());
        assertTrue(decision.getArgumentsJson().contains("AI资本开支 下调 风险"));
        assertTrue(decision.getActionFingerprint().startsWith("public_news_search:"));
    }

    @Test
    void rejectsUnknownToolUnknownArgumentAndInvalidConfidence() {
        ResearchDecisionDraft unsafe = searchDraft();
        unsafe.setToolCode("shell");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unsafe, context, "MODEL"));

        ResearchDecisionDraft unknownArgument = searchDraft();
        unknownArgument.getArguments().put("url", "https://example.com");
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(unknownArgument, context, "MODEL"));

        ResearchDecisionDraft invalidConfidence = searchDraft();
        invalidConfidence.setConfidence(1.2D);
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidConfidence, context, "MODEL"));
    }

    @Test
    void rejectsMalformedFinishAndRepeatedActionFingerprint() {
        ResearchDecisionDraft finish = new ResearchDecisionDraft();
        finish.setDecisionType("FINISH");
        finish.setCurrentSubgoal("结束研究");
        finish.setDecisionSummary("证据已经充分");
        finish.setConfidence(0.8D);
        finish.setToolCode("report_synthesis");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(finish, context, "MODEL"));

        ResearchAgentDecision accepted = validator.validate(searchDraft(), context, "MODEL");
        context.setAttemptedFingerprints(Arrays.asList(accepted.getActionFingerprint()));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(searchDraft(), context, "MODEL"));
    }

    @Test
    void validatesLocalPlanPatchAsPersistedDecisionArguments() {
        ResearchDecisionDraft patch = new ResearchDecisionDraft();
        patch.setDecisionType("PLAN_PATCH");
        patch.setCurrentSubgoal("改用一手材料补齐反方证据");
        patch.setDecisionSummary("连续搜索无进展，局部替换未执行任务");
        patch.setConfidence(0.75D);
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("operation", "ADD_OR_REPLACE_PENDING_TASK");
        values.put("taskKey", "adaptive_counter_2");
        values.put("title", "寻找需求下修的一手材料");
        values.put("question", "是否存在指引下修？");
        values.put("toolCode", "public_news_search");
        values.put("intent", "COUNTER");
        values.put("queryText", "光模块 指引 下调 公司公告");
        values.put("reason", "原查询没有新增独立来源");
        patch.setPlanPatch(values);

        ResearchAgentDecision decision = validator.validate(patch, context, "MODEL");

        assertEquals("PLAN_PATCH", decision.getDecisionType());
        assertTrue(decision.getArgumentsJson().contains("adaptive_counter_2"));
    }

    @Test
    void appliesExternalBudgetOnlyToPublicSearch() {
        context.setRemainingActions(0);

        ResearchAgentDecision assessment = validator.validate(assessmentDraft(), context, "MODEL");

        assertEquals("evidence_assess", assessment.getToolCode());
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(searchDraft(), context, "MODEL"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(materialDraft(), context, "MODEL"));
    }

    @Test
    void validatesStructuredMaterialToolWithExactArguments() {
        ResearchAgentDecision decision = validator.validate(materialDraft(), context, "MODEL");

        assertEquals("research_material_search", decision.getToolCode());
        assertTrue(decision.getArgumentsJson().contains("ANNOUNCEMENT"));

        ResearchDecisionDraft extra = materialDraft();
        extra.getArguments().put("limit", 100);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(extra, context, "MODEL"));

        ResearchDecisionDraft invalidCode = materialDraft();
        invalidCode.getArguments().put("stockCode", "NVDA");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidCode, context, "MODEL"));
    }

    private ResearchDecisionDraft searchDraft() {
        ResearchDecisionDraft draft = new ResearchDecisionDraft();
        draft.setDecisionType("TOOL_CALL");
        draft.setCurrentSubgoal("补齐反方证据");
        draft.setToolCode("public_news_search");
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("query", "AI资本开支 下调 风险");
        arguments.put("intent", "COUNTER");
        draft.setArguments(arguments);
        draft.setTargetGap("counter=0");
        draft.setExpectedObservation("获得独立反方来源");
        draft.setDecisionSummary("当前证据单边，优先寻找反方材料");
        draft.setConfidence(0.82D);
        return draft;
    }

    private ResearchDecisionDraft assessmentDraft() {
        ResearchDecisionDraft draft = new ResearchDecisionDraft();
        draft.setDecisionType("TOOL_CALL");
        draft.setCurrentSubgoal("刷新证据缺口判断");
        draft.setToolCode("evidence_assess");
        draft.setArguments(Collections.<String, Object>emptyMap());
        draft.setTargetGap("搜索后需要重新统计");
        draft.setExpectedObservation("获得最新证据状态");
        draft.setDecisionSummary("搜索已结束，先执行本地证据评估");
        draft.setConfidence(0.9D);
        return draft;
    }

    private ResearchDecisionDraft materialDraft() {
        ResearchDecisionDraft draft = new ResearchDecisionDraft();
        draft.setDecisionType("TOOL_CALL");
        draft.setCurrentSubgoal("查找公司一手公告");
        draft.setToolCode("research_material_search");
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("stockCode", "000001");
        arguments.put("materialType", "ANNOUNCEMENT");
        arguments.put("query", "半年度报告");
        draft.setArguments(arguments);
        draft.setTargetGap("primary=0");
        draft.setExpectedObservation("获得公司披露的一手材料");
        draft.setDecisionSummary("优先用公告建立事实基线");
        draft.setConfidence(0.9D);
        return draft;
    }
}
