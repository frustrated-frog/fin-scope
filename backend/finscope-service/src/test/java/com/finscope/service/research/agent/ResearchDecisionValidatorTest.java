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
}
