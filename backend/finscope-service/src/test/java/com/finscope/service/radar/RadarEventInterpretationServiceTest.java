package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEventInterpretationRepository;
import com.finscope.dao.radar.RadarEvidenceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarEventInterpretationServiceTest {
    private RadarEventInterpretationRepository interpretations;
    private RadarRepository radar;
    private RadarEvidenceRepository evidence;
    private RadarEventInterpretationAgent agent;
    private CapturingExecutor executor;
    private RadarEventInterpretationService service;
    private RadarEvent event;
    private RadarSignal signal;

    @BeforeEach
    void setUp() {
        interpretations = mock(RadarEventInterpretationRepository.class);
        radar = mock(RadarRepository.class); evidence = mock(RadarEvidenceRepository.class);
        agent = mock(RadarEventInterpretationAgent.class); executor = new CapturingExecutor();
        service = new RadarEventInterpretationService(interpretations, radar, evidence, agent, executor);
        event = new RadarEvent(); event.setId(10L); event.setCanonicalTitle("宁德时代发布新一代电池");
        event.setSummary("新品正式发布");
        signal = new RadarSignal(); signal.setId(1L); signal.setContentHash("signal-hash");
        signal.setTitle("新品正式发布"); signal.setContent("披露量产时间");
        when(radar.findEvent(10L)).thenReturn(Optional.of(event));
        when(radar.findSignalsByEventId(10L)).thenReturn(Collections.singletonList(signal));
        when(evidence.findByEventId(10L)).thenReturn(Collections.emptyList());
    }

    @Test
    void returnsQueuedBeforeTheAgentRuns() {
        RadarEventInterpretation queued = queued();
        when(interpretations.findByEventFingerprint(eq(10L), any())).thenReturn(Optional.empty());
        when(interpretations.saveQueued(eq(10L), any())).thenReturn(queued);
        when(agent.interpret(eq(event), any(), any())).thenReturn(successResult());

        RadarEventInterpretation returned = service.request(10L);

        assertEquals("QUEUED", returned.getStatus());
        assertEquals(1, executor.tasks.size());
        verify(agent, never()).interpret(any(), any(), any());

        executor.runNext();
        ArgumentCaptor<RadarEventInterpretation> updates = ArgumentCaptor.forClass(RadarEventInterpretation.class);
        verify(interpretations, org.mockito.Mockito.atLeast(2)).update(updates.capture());
        assertEquals("SUCCESS", updates.getValue().getStatus());
    }

    @Test
    void duplicateRequestsForTheSameVersionScheduleOnlyOnce() {
        RadarEventInterpretation queued = queued();
        when(interpretations.findByEventFingerprint(eq(10L), any()))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(queued));
        when(interpretations.saveQueued(eq(10L), any())).thenReturn(queued);

        service.request(10L);
        RadarEventInterpretation duplicate = service.request(10L);

        assertEquals(queued.getId(), duplicate.getId());
        assertEquals(1, executor.tasks.size());
    }

    @Test
    void reusesSuccessfulInterpretationForAnUnchangedEvent() {
        RadarEventInterpretation success = queued(); success.setStatus("SUCCESS");
        when(interpretations.findByEventFingerprint(eq(10L), any())).thenReturn(Optional.of(success));

        RadarEventInterpretation returned = service.request(10L);

        assertEquals("SUCCESS", returned.getStatus());
        assertTrue(executor.tasks.isEmpty());
    }

    @Test
    void marksInvalidAgentOutputAsFailedWithoutThrowingFromTheRequest() {
        RadarEventInterpretation queued = queued();
        when(interpretations.findByEventFingerprint(eq(10L), any())).thenReturn(Optional.empty());
        when(interpretations.saveQueued(eq(10L), any())).thenReturn(queued);
        when(agent.interpret(eq(event), any(), any())).thenThrow(
                new RadarEventInterpretationAgent.InterpretationException("INVALID_OUTPUT", "bad json"));

        RadarEventInterpretation returned = service.request(10L);
        executor.runNext();

        assertEquals("FAILED", returned.getStatus());
        assertEquals("INVALID_OUTPUT", returned.getFailureCode());
        assertFalse(returned.getFailureMessage().isEmpty());
    }

    @Test
    void marksAnOlderInterpretationAsStaleForTheCurrentEventVersion() {
        RadarEventInterpretation old = queued(); old.setStatus("SUCCESS"); old.setEventFingerprint("old-fingerprint");
        when(interpretations.findByEventFingerprint(eq(10L), any())).thenReturn(Optional.empty());
        when(interpretations.findLatestByEventId(10L)).thenReturn(Optional.of(old));

        RadarEventInterpretation current = service.current(event, Collections.singletonList(signal),
                Collections.<RadarEvidence>emptyList()).get();

        assertTrue(current.isStale());
    }

    private RadarEventInterpretation queued() {
        RadarEventInterpretation value = new RadarEventInterpretation(); value.setId(5L); value.setEventId(10L);
        value.setEventFingerprint("current-fingerprint"); value.setStatus("QUEUED"); return value;
    }

    private RadarEventInterpretation.Result successResult() {
        RadarEventInterpretation.Result result = new RadarEventInterpretation.Result();
        result.setFactSummary("事实摘要"); result.setNewDevelopment("新增变化"); result.setWhyItMatters("研究价值");
        result.setImpactChain(Collections.singletonList("影响链"));
        result.setUncertainties(Collections.singletonList("待确认"));
        result.setNextObservations(Collections.singletonList("后续观察")); return result;
    }

    private static final class CapturingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<Runnable>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.remove(0).run(); }
    }
}
