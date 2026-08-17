package com.finscope.service.globalexpectations;

import com.finscope.dao.cache.GlobalExpectationsCacheRepository;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExpectationEnhancementServiceTest {
    @Test
    void asynchronouslyCachesOneInterpretationPerUnchangedSignalFingerprint() {
        FakeCache cache = new FakeCache();
        CountingAgent agent = new CountingAgent();
        GlobalExpectationEnhancementService service = new GlobalExpectationEnhancementService();
        ReflectionTestUtils.setField(service, "cacheRepository", cache);
        ReflectionTestUtils.setField(service, "agent", agent);
        ReflectionTestUtils.setField(service, "executor", (java.util.concurrent.Executor) Runnable::run);
        GlobalExpectationEventGroup group = group();

        service.request(List.of(group));
        service.request(List.of(group));
        service.attachCached(List.of(group));

        assertEquals(1, agent.calls);
        assertEquals("READY", group.getInterpretation().getStatus());
        assertNotNull(group.getInterpretation().getFingerprint());
    }

    @Test
    void enhancesWatchingCardsAndKeepsRuleTextVisibleWhileQueued() {
        FakeCache cache = new FakeCache();
        List<Runnable> tasks = new ArrayList<Runnable>();
        GlobalExpectationEnhancementService service = service(cache, new CountingAgent(), tasks::add);
        GlobalExpectationEventGroup group = group();
        group.setStatus("WATCHING");
        group.getInterpretation().setHappened("规则快读仍然可见");

        service.request(List.of(group));

        assertEquals(1, tasks.size());
        assertEquals("QUEUED", group.getInterpretation().getStatus());
        assertEquals("规则快读仍然可见", group.getInterpretation().getHappened());
        assertEquals("RULE", group.getInterpretation().getSource());
    }

    @Test
    void keepsFingerprintStableForNoiseInsideProbabilityBucket() {
        FakeCache cache = new FakeCache();
        CountingAgent agent = new CountingAgent();
        GlobalExpectationEnhancementService service = service(cache, agent, Runnable::run);
        GlobalExpectationEventGroup group = group();

        service.request(List.of(group));
        group.getMarkets().get(0).setProbability(54);
        group.getMarkets().get(0).setVolume24h(999999D);
        group.getMarkets().get(0).setRank(9);
        service.request(List.of(group));
        group.getMarkets().get(0).setProbability(57);
        service.request(List.of(group));

        assertEquals(2, agent.calls);
    }

    @Test
    void cachedCardsDoNotConsumeTheFiveTaskRefreshBudget() {
        FakeCache cache = new FakeCache();
        CountingAgent agent = new CountingAgent();
        GlobalExpectationEnhancementService service = service(cache, agent, Runnable::run);
        List<GlobalExpectationEventGroup> groups = new ArrayList<GlobalExpectationEventGroup>();
        for (int index = 0; index < 7; index++) {
            GlobalExpectationEventGroup group = group();
            group.setId("event:" + index);
            groups.add(group);
        }

        service.request(groups.subList(0, 2));
        service.request(groups);

        assertEquals(7, agent.calls);
    }

    private GlobalExpectationEnhancementService service(FakeCache cache, CountingAgent agent, Executor executor) {
        GlobalExpectationEnhancementService service = new GlobalExpectationEnhancementService();
        ReflectionTestUtils.setField(service, "cacheRepository", cache);
        ReflectionTestUtils.setField(service, "agent", agent);
        ReflectionTestUtils.setField(service, "executor", executor);
        return service;
    }

    private GlobalExpectationEventGroup group() {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setId("event:fed");
        group.setTitle("美联储利率决议");
        group.setStatus("SIGNAL");
        group.setSignalScore(80);
        group.setSignalReasons(List.of("1小时概率显著上升"));
        GlobalExpectationItem market = new GlobalExpectationItem();
        market.setMarketId("fed-september");
        market.setProbability(52);
        market.setVolume24h(100000D);
        market.setRank(1);
        group.setMarkets(List.of(market));
        group.setRadarMatches(List.of());
        GlobalExpectationInterpretation rule = new GlobalExpectationInterpretation();
        rule.setStatus("RULE");
        rule.setSource("RULE");
        rule.setHappened("规则快读");
        rule.setMeaning("规则含义");
        rule.setRelatedVariables("利率与通胀");
        rule.setNextObservation("关注决议");
        rule.setUncertainty("价格不是事实概率");
        group.setInterpretation(rule);
        return group;
    }

    private static final class CountingAgent extends GlobalExpectationInterpretationAgent {
        private int calls;

        @Override
        public GlobalExpectationInterpretation interpret(GlobalExpectationEventGroup group) {
            calls++;
            GlobalExpectationInterpretation result = new GlobalExpectationInterpretation();
            result.setStatus("READY");
            result.setSource("AI");
            result.setHappened("概率显著变化");
            return result;
        }
    }

    private static final class FakeCache implements GlobalExpectationsCacheRepository {
        private final Map<String, GlobalExpectationInterpretation> interpretations =
                new HashMap<String, GlobalExpectationInterpretation>();

        @Override
        public Optional<com.finscope.domain.globalexpectations.GlobalExpectationHistorySnapshot> getHistory(
                String tokenId) {
            return Optional.empty();
        }

        @Override
        public void putHistory(com.finscope.domain.globalexpectations.GlobalExpectationHistorySnapshot snapshot) {
        }

        @Override
        public Optional<com.finscope.domain.globalexpectations.GlobalExpectationsViewSnapshot> getView() {
            return Optional.empty();
        }

        @Override
        public void putView(com.finscope.domain.globalexpectations.GlobalExpectationsViewSnapshot snapshot) {
        }

        @Override
        public Optional<GlobalExpectationInterpretation> getInterpretation(String groupId) {
            return Optional.ofNullable(interpretations.get(groupId));
        }

        @Override
        public void putInterpretation(String groupId, GlobalExpectationInterpretation interpretation) {
            interpretations.put(groupId, interpretation);
        }
    }
}
