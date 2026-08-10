package com.finscope.service.learningcard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.learningcard.StockLearningCardClaim;
import com.finscope.domain.learningcard.StockLearningCardEvidence;
import com.finscope.rpc.llm.LlmChatClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockLearningCardSynthesisAgentTest {
    @Test
    void parsesTheSpaceSpecificSectionContract() throws Exception {
        LlmChatClient llm = model(spaceJson("E1", ""));
        StockLearningCardSynthesisAgent agent = new StockLearningCardSynthesisAgent(llm, new ObjectMapper());

        StockLearningCardClaim claim = agent.synthesize("杭电股份", "603618", "SPACE",
                Collections.singletonList(evidence()));

        assertEquals("增长取决于新业务兑现", claim.getHeadline());
        assertEquals("成长空间", claim.getRatingLabel());
        assertEquals("MEDIUM_HIGH", claim.getRatingValue());
        assertEquals(5, claim.getSections().size());
        assertEquals("growth_drivers", claim.getSections().get(1).getKey());
        assertEquals(Collections.singletonList("E1"), claim.getSections().get(1).getEvidenceRefs());
        assertEquals("SUPPORTED", claim.getSections().get(1).getVerificationStatus());
    }

    @Test
    void rejectsUnknownSectionsAndEvidenceOutsideTheInputPacket() throws Exception {
        StockLearningCardSynthesisAgent unknownSection = new StockLearningCardSynthesisAgent(
                model(spaceJson("E1", ",{" + section("invented", "模型自造栏目", "内容", "E1") + "}")), new ObjectMapper());
        StockLearningCardSynthesisAgent unknownEvidence = new StockLearningCardSynthesisAgent(
                model(spaceJson("E9", "")), new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> unknownSection.synthesize(
                "杭电股份", "603618", "SPACE", Collections.singletonList(evidence())));
        assertThrows(IllegalArgumentException.class, () -> unknownEvidence.synthesize(
                "杭电股份", "603618", "SPACE", Collections.singletonList(evidence())));
    }

    @Test
    void rejectsFieldsOutsideTheLearningCardContract() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn("{\"judgment\":\"判断\",\"rationale\":\"依据[E1]\","
                + "\"counterargument\":\"反方\",\"unknowns\":\"未知\",\"confidence\":\"LOW\",\"theme\":\"不应出现\"}");
        StockLearningCardSynthesisAgent agent = new StockLearningCardSynthesisAgent(llm, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> agent.synthesize("杭电股份", "603618", "SPACE",
                Collections.singletonList(new StockLearningCardEvidence("E1", "公告", "url", "source", "", "正文"))));
    }

    @Test
    void returnsAnExplicitInsufficientResultWhenTheModelIsUnavailable() throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(false);
        StockLearningCardSynthesisAgent agent = new StockLearningCardSynthesisAgent(llm, new ObjectMapper());

        StockLearningCardClaim claim = agent.synthesize("杭电股份", "603618", "SPACE",
                Collections.singletonList(new StockLearningCardEvidence("E1", "公告", "url", "source", "", "正文")));

        assertEquals("INSUFFICIENT_EVIDENCE", claim.getStatus());
        assertEquals("LOW", claim.getConfidence());
        assertEquals(5, claim.getSections().size());
        assertEquals("UNVERIFIED", claim.getSections().get(0).getVerificationStatus());
    }

    private LlmChatClient model(String output) throws Exception {
        LlmChatClient llm = mock(LlmChatClient.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(output);
        return llm;
    }

    private StockLearningCardEvidence evidence() {
        return new StockLearningCardEvidence("E1", "公告", "url", "source", "", "正文");
    }

    private String spaceJson(String evidenceRef, String extraSection) {
        return "{\"headline\":\"增长取决于新业务兑现\",\"ratingValue\":\"MEDIUM_HIGH\",\"sections\":["
                + "{" + section("business_map", "当前业务版图", "传统业务与新业务并存", evidenceRef) + "},"
                + "{" + section("growth_drivers", "增量引擎", "新业务提供增量", evidenceRef) + "},"
                + "{" + section("capture_capacity", "公司承接能力", "产能仍待验证", evidenceRef) + "},"
                + "{" + section("milestones", "兑现路径", "等待客户认证", evidenceRef) + "},"
                + "{" + section("constraints", "增长约束", "投产节奏是约束", evidenceRef) + "}"
                + extraSection + "],\"confidence\":\"MEDIUM\"}";
    }

    private String section(String key, String title, String content, String evidenceRef) {
        return "\"key\":\"" + key + "\",\"title\":\"" + title + "\",\"content\":\"" + content
                + "\",\"evidenceRefs\":[\"" + evidenceRef + "\"],\"verificationStatus\":\"SUPPORTED\"";
    }
}
