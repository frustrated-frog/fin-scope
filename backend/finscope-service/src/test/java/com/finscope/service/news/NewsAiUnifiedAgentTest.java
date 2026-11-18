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
        decision.setTargetKey("e1");
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

    @Test
    void wireIdsStayShortAndFactsPrecedeRelationshipWhileStoredIdentitiesStayIntact() throws Exception {
        var agent = agent();
        var input = input();
        String actual = "a".repeat(64) + "@2026-11-17";
        input.setContentKey(actual);
        var next = input();
        next.setContentKey("b".repeat(64) + "@2026-11-17");
        var seed = new NewsAiEvent();
        seed.setEventKey("news-ai:" + actual);
        next.setCandidates(List.of(input.getCandidates().getFirst(), seed));
        var request = mapper.readTree(agent.prepare(List.of(input, next), categories).getBody());
        var wire = mapper.readTree(request.path("input").get(1).path("content").asText());
        assertEquals("n1", wire.path("items").get(0).path("contentKey").asText());
        assertEquals("e1", wire.path("items").get(0).path("candidateKeys").get(0).asText());
        assertEquals("n1", wire.path("items").get(1).path("candidateKeys").get(1).asText());
        assertEquals(1, wire.path("candidates").size());
        assertFalse(wire.path("items").get(0).has("candidates"));
        assertFalse(request.toString().contains(actual));
        assertEquals(actual, input.getContentKey());
        assertEquals("news-ai:" + actual, seed.getEventKey());
        var fields = request.path("text").path("format").path("schema").path("properties")
                .path("decisions").path("items").path("properties");
        List<String> order = new ArrayList<>();
        fields.fieldNames().forEachRemaining(order::add);
        assertEquals(List.of("contentKey", "analysis", "relation", "targetKey", "newFacts", "reason"), order);
        var first = decision();
        var second = decision();
        second.setContentKey("n2");
        second.setTargetKey("n1");
        second.setRelation(NewsAiRelation.DUPLICATE);
        var parsed = agent.parse(mapper.writeValueAsString(Map.of("decisions", List.of(first, second))), List.of(input, next), categories);
        assertEquals(2, parsed.size());
        assertEquals("news-ai:" + actual, parsed.get(next.getContentKey()).getTargetKey());
        first.setTargetKey("n1");
        first.setRelation(NewsAiRelation.DUPLICATE);
        parsed = agent.parse(mapper.writeValueAsString(Map.of("decisions", List.of(first, second))), List.of(input, next), categories);
        assertFalse(parsed.containsKey(actual));
        assertTrue(parsed.containsKey(next.getContentKey()));
    }

    @Test
    void validUnderstandingSurvivesAnInvalidRelationshipWithoutResendingTheBody() throws Exception {
        var agent = agent();
        var item = input();
        var decision = decision();
        decision.setRelation(NewsAiRelation.DUPLICATE);
        decision.setTargetKey("not-in-whitelist");
        String text = mapper.writeValueAsString(Map.of("decisions", List.of(decision)));
        assertTrue(agent.parse(text, List.of(item), categories).isEmpty());
        assertEquals("合同金额1.2亿元", agent.parseAnalyses(text, List.of(item), categories).get("hash").getSummary());
        decision.getAnalysis().setCategoryCode("UNKNOWN");
        assertTrue(agent.parseAnalyses(mapper.writeValueAsString(Map.of("decisions", List.of(decision))), List.of(item), categories).isEmpty());
    }

    @Test
    void aSharedCandidateIsTransmittedOnceForTheWholeBatch() throws Exception {
        var items = new ArrayList<NewsAiWorkItem>();
        for (int i = 0; i < 6; i++) {
            var item = input();
            item.setContentKey("hash" + i);
            var event = item.getCandidates().getFirst();
            event.setAnalysis(decision().getAnalysis());
            event.getAnalysis().setSummary("同一份既有事件事实卡".repeat(40));
            items.add(item);
        }
        var request = mapper.readTree(agent().prepare(items, categories).getBody());
        var payload = mapper.readTree(request.path("input").get(1).path("content").asText());
        assertEquals(1, payload.path("candidates").size());
        for (var item : payload.path("items")) {
            assertEquals("e1", item.path("candidateKeys").get(0).asText());
        }
        long legacyCandidateBytes = mapper.writeValueAsBytes(items.getFirst().getCandidates().getFirst().getAnalysis()).length * 6L;
        assertTrue(mapper.writeValueAsBytes(payload).length < legacyCandidateBytes / 2);
    }

    @Test
    void reclassificationCannotRewritePreviouslyUnderstoodFacts() throws Exception {
        var agent = agent();
        var item = input();
        var old = decision().getAnalysis();
        old.setCategoryCode("OLD");
        old.setFacts(List.of("合同金额1.2亿元"));
        item.setSavedAnalysis(old);
        item.setMode(NewsAiInputMode.RECLASSIFY);
        var result = decision();
        result.getAnalysis().setFacts(old.getFacts());
        String text = mapper.writeValueAsString(Map.of("decisions", List.of(result)));
        assertEquals(1, agent.parse(text, List.of(item), categories).size());
        result.getAnalysis().setFacts(List.of("合同金额12亿元"));
        text = mapper.writeValueAsString(Map.of("decisions", List.of(result)));
        assertTrue(agent.parse(text, List.of(item), categories).isEmpty());
        assertTrue(agent.parseAnalyses(text, List.of(item), categories).isEmpty());
    }

    private NewsAiUnifiedAgent agent() {
        var client = new com.finscope.rpc.llm.NewsAiLunaClient();
        ReflectionTestUtils.setField(client, "mapper", mapper);
        ReflectionTestUtils.setField(client, "config", new NewsAiProviderConfig());
        var agent = new NewsAiUnifiedAgent();
        ReflectionTestUtils.setField(agent, "mapper", mapper);
        ReflectionTestUtils.setField(agent, "client", client);
        ReflectionTestUtils.setField(agent, "properties", new NewsAiProperties());
        return agent;
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
        decision.setContentKey("n1");
        decision.setAnalysis(analysis);
        decision.setRelation(NewsAiRelation.NEW_EVENT);
        decision.setTargetKey("");
        decision.setNewFacts(List.of());
        decision.setReason("原文披露合同");
        return decision;
    }
}
