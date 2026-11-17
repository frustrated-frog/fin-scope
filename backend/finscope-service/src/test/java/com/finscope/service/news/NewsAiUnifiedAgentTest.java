package com.finscope.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.*;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NewsAiUnifiedAgentTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final List<NewsCategory> categories = List.of(new NewsCategory("COMPANY", "公司", "公司业务", true, 1));

    @Test
    void rejectsInventedTargetsCategoriesAndContradictoryRelations() throws Exception {
        NewsAiWorkItem input = input();
        NewsAiDecision decision = decision();
        assertEquals(1, parse(List.of(decision), input).size());
        decision.setTargetKey("invented");
        decision.setRelation(NewsAiRelation.DUPLICATE);
        assertTrue(parse(List.of(decision), input).isEmpty());
        decision.setTargetKey("known");
        decision.setNewFacts(List.of("新的金额"));
        assertTrue(parse(List.of(decision), input).isEmpty());
        decision.setRelation(NewsAiRelation.PROGRESS);
        assertEquals(1, parse(List.of(decision), input).size());
        decision.setNewFacts(List.of());
        assertTrue(parse(List.of(decision), input).isEmpty());
        decision.setRelation(NewsAiRelation.NEW_EVENT);
        decision.setTargetKey("");
        decision.getAnalysis().setCategoryCode("INVENTED");
        assertTrue(parse(List.of(decision), input).isEmpty());
    }

    @Test
    void duplicateIdsInvalidateTheItemAndSavedUnderstandingCannotBeRewritten() throws Exception {
        var input = input();
        var decision = decision();
        assertTrue(parse(List.of(decision, decision), input).isEmpty());
        input.setSavedAnalysis(decision.getAnalysis());
        decision.setAnalysis(null);
        var result = parse(List.of(decision), input);
        assertSame(input.getSavedAnalysis(), result.get("hash").getAnalysis());
    }

    @Test
    void candidateCardsAreBoundedAndStillRetrieveUnlistedEntities() {
        var selector = new NewsAiCandidateSelector();
        var target = new NewsAiEvent();
        target.setEventKey("target");
        target.setAnalysis(decision().getAnalysis());
        target.getAnalysis().setTitle("新创企业珀云发布光学计算产品");
        target.getAnalysis().setSummary("产品技术细节".repeat(1000));
        target.getAnalysis().setFacts(Collections.nCopies(32, "技术参数".repeat(100)));
        var other = new NewsAiEvent();
        other.setEventKey("other");
        other.setAnalysis(decision().getAnalysis());
        other.getAnalysis().setTitle("商品期货市场报价");
        var selected = selector.index(Map.of("target", target, "other", other)).select("珀云推出光学计算新品", "技术产品性能升级", 1);
        assertEquals("target", selected.getFirst().getEventKey());
        assertTrue(selected.getFirst().getAnalysis().getSummary().length() <= 400);
        assertEquals(6, selected.getFirst().getAnalysis().getFacts().size());
        assertTrue(selected.getFirst().getAnalysis().getFacts().getFirst().length() <= 120);
    }

    @Test
    void contentIdentityKeepsFinancialSignsDecimalsAndNegation() {
        assertEquals(NewsAiContentIdentity.hash("公司 公告", "净利润 1.2亿元"), NewsAiContentIdentity.hash("公司  公告", "净利润  1.2亿元"));
        assertNotEquals(NewsAiContentIdentity.hash("公司公告", "净利润1.2亿元"), NewsAiContentIdentity.hash("公司公告", "净利润12亿元"));
        assertNotEquals(NewsAiContentIdentity.hash("公司公告", "同比+5%"), NewsAiContentIdentity.hash("公司公告", "同比-5%"));
        assertNotEquals(NewsAiContentIdentity.hash("公司公告", "计划增持"), NewsAiContentIdentity.hash("公司公告", "不计划增持"));
    }

    private Map<String, NewsAiDecision> parse(List<NewsAiDecision> decisions, NewsAiWorkItem input) throws Exception {
        var agent = new NewsAiUnifiedAgent();
        ReflectionTestUtils.setField(agent, "mapper", mapper);
        return agent.parse(mapper.writeValueAsString(Map.of("decisions", decisions)), List.of(input), categories);
    }

    private NewsAiWorkItem input() {
        var input = new NewsAiWorkItem();
        input.setContentKey("hash");
        var event = new NewsAiEvent();
        event.setEventKey("known");
        input.setCandidates(List.of(event));
        return input;
    }

    private NewsAiDecision decision() {
        var analysis = new NewsAiAnalysis();
        analysis.setCategoryCode("COMPANY");
        analysis.setDisplayCategory(NewsAiDisplayCategory.FINANCE);
        analysis.setEventType(NewsAiEventType.TRANSACTION);
        analysis.setTitle("公司合同进展");
        analysis.setSummary("合同金额1.2亿元");
        var decision = new NewsAiDecision();
        decision.setContentKey("hash");
        decision.setAnalysis(analysis);
        decision.setRelation(NewsAiRelation.NEW_EVENT);
        decision.setTargetKey("");
        decision.setNewFacts(List.of());
        decision.setReason("原文披露合同");
        return decision;
    }
}
