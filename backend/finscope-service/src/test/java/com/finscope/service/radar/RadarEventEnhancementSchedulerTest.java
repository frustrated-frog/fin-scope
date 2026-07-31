package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarEventEnhancementSchedulerTest {
    @Test
    void titleAndEvidenceAgentsNeverRunOnRequestThread() {
        RadarCanonicalTitleAgent titles=mock(RadarCanonicalTitleAgent.class);
        RadarEvidenceOrchestrator evidence=mock(RadarEvidenceOrchestrator.class);
        RadarRepository repository=mock(RadarRepository.class);
        CapturingExecutor executor=new CapturingExecutor();
        when(titles.generate(anyList(),any())).thenReturn(RadarCanonicalTitleAgent.Result.fallback("规则标题","TEST"));
        when(evidence.enrich(any(RadarEvent.class),anyList())).thenReturn(
                new RadarEvidenceOrchestrator.Outcome("SUCCESS","证据完成","",2,2,"fp"));
        RadarEventEnhancementScheduler scheduler=new RadarEventEnhancementScheduler(titles,evidence,repository,executor);

        scheduler.schedule(event(),Arrays.asList(signal(1L),signal(2L)),LocalDateTime.of(2026,7,31,20,0),true);

        verify(titles,never()).generate(anyList(),any());
        verify(evidence,never()).enrich(any(RadarEvent.class),anyList());
        executor.runPending();
        verify(titles).generate(anyList(),any());
        verify(evidence).enrich(any(RadarEvent.class),anyList());
        verify(repository).updateEvidenceEnhancement(any(RadarEvent.class));
    }

    @Test
    void titleEnhancementDoesNotConsumeEvidenceForLowerPriorityEvent() {
        RadarCanonicalTitleAgent titles=mock(RadarCanonicalTitleAgent.class);
        RadarEvidenceOrchestrator evidence=mock(RadarEvidenceOrchestrator.class);
        CapturingExecutor executor=new CapturingExecutor();
        when(titles.generate(anyList(),any())).thenReturn(RadarCanonicalTitleAgent.Result.fallback("规则标题","TEST"));
        RadarEventEnhancementScheduler scheduler=new RadarEventEnhancementScheduler(
                titles,evidence,mock(RadarRepository.class),executor);

        scheduler.schedule(event(),Arrays.asList(signal(1L),signal(2L)),LocalDateTime.of(2026,7,31,20,0),false);
        executor.runPending();

        verify(titles).generate(anyList(),any());
        verify(evidence,never()).enrich(any(RadarEvent.class),anyList());
    }

    private RadarEvent event(){RadarEvent value=new RadarEvent();value.setId(8L);value.setEventKey("event:8");value.setCanonicalTitle("规则标题");value.setPriorityScore(82);return value;}
    private RadarSignal signal(Long id){RadarSignal value=new RadarSignal();value.setId(id);value.setTitle("信号"+id);return value;}
    private static final class CapturingExecutor implements Executor {private Runnable pending;public void execute(Runnable command){pending=command;}void runPending(){pending.run();pending=null;}}
}
