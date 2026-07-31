package com.finscope.service.radar;

import com.finscope.dao.radar.RadarPairDecisionRepository;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarPairDecisionSchedulerTest {
    @Test
    void modelDecisionRunsOnlyAfterBackgroundExecutorStarts() {
        RadarEventMatchAgent agent=mock(RadarEventMatchAgent.class);
        RadarPairDecisionRepository repository=mock(RadarPairDecisionRepository.class);
        CapturingExecutor executor=new CapturingExecutor();
        when(agent.decide(any(),any())).thenReturn(RadarEventMatchAgent.Decision.agent(true,0.9,"同一事件"));
        RadarPairDecisionScheduler scheduler=new RadarPairDecisionScheduler(agent,repository,executor);

        scheduler.schedule(signal(1L),signal(2L),"left-fingerprint","right-fingerprint");

        verify(agent,never()).decide(any(),any());
        verify(repository,never()).save(any(RadarPairDecision.class));
        executor.runPending();
        verify(agent).decide(any(),any());
        verify(repository).save(any(RadarPairDecision.class));
    }

    @Test
    void duplicatePairIsOnlyQueuedOnceWhileInFlight() {
        CapturingExecutor executor=new CapturingExecutor();
        RadarPairDecisionScheduler scheduler=new RadarPairDecisionScheduler(
                mock(RadarEventMatchAgent.class),mock(RadarPairDecisionRepository.class),executor);

        scheduler.schedule(signal(1L),signal(2L),"left","right");
        scheduler.schedule(signal(2L),signal(1L),"right","left");

        org.junit.jupiter.api.Assertions.assertEquals(1,executor.submissions);
    }

    private RadarSignal signal(Long id){RadarSignal value=new RadarSignal();value.setId(id);value.setTitle("信号"+id);return value;}
    private static final class CapturingExecutor implements Executor {
        private Runnable pending; private int submissions;
        public void execute(Runnable command){submissions++;pending=command;}
        void runPending(){Runnable task=pending;pending=null;task.run();}
    }
}
