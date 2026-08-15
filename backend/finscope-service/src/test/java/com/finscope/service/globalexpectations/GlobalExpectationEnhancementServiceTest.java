package com.finscope.service.globalexpectations;

import com.finscope.dao.cache.GlobalExpectationsCacheRepository;
import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationInterpretation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

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

    private GlobalExpectationEventGroup group() {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setId("event:fed");
        group.setTitle("美联储利率决议");
        group.setStatus("SIGNAL");
        group.setSignalScore(80);
        group.setSignalReasons(List.of("1小时概率显著上升"));
        group.setMarkets(List.of());
        group.setRadarMatches(List.of());
        return group;
    }

    private static final class CountingAgent extends GlobalExpectationInterpretationAgent {
        private int calls;

        @Override
        public GlobalExpectationInterpretation interpret(GlobalExpectationEventGroup group) {
            calls++;
            GlobalExpectationInterpretation result = new GlobalExpectationInterpretation();
            result.setStatus("READY");
            result.setHappened("概率显著变化");
            return result;
        }
    }

    private static final class FakeCache implements GlobalExpectationsCacheRepository {
        private GlobalExpectationInterpretation interpretation;

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
            return Optional.ofNullable(interpretation);
        }

        @Override
        public void putInterpretation(String groupId, GlobalExpectationInterpretation interpretation) {
            this.interpretation = interpretation;
        }
    }
}
