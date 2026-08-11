package com.finscope.service.industrychain;

import com.finscope.dao.industrychain.IndustryChainEventImpactRepository;
import com.finscope.dao.industrychain.IndustryChainRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndustryChainEventServiceTest {

    @Test
    void refreshOnlyAnalyzesRadarEventsThatHaveNotBeenLinked() {
        IndustryChainRepository chains = mock(IndustryChainRepository.class);
        IndustryChainEventImpactRepository impacts = mock(IndustryChainEventImpactRepository.class);
        RadarRepository radar = mock(RadarRepository.class);
        IndustryChainEventAnalyzer analyzer = mock(IndustryChainEventAnalyzer.class);
        IndustryChainEventService service = new IndustryChainEventService();
        ReflectionTestUtils.setField(service, "chainRepository", chains);
        ReflectionTestUtils.setField(service, "impactRepository", impacts);
        ReflectionTestUtils.setField(service, "radarRepository", radar);
        ReflectionTestUtils.setField(service, "analyzer", analyzer);
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setChainId(1L);
        RadarEvent event = new RadarEvent();
        event.setId(7L);
        event.setLastSeenAt(LocalDateTime.now());
        IndustryChainEventImpact impact = new IndustryChainEventImpact();
        impact.setChainId(1L);
        impact.setRadarEventId(7L);
        when(chains.findPublishedGraph(1L)).thenReturn(Optional.of(graph));
        when(radar.findEventsSince(any(LocalDateTime.class), anyInt())).thenReturn(Collections.singletonList(event));
        when(analyzer.getAnalysisVersion()).thenReturn("RULES_V2");
        when(impacts.findAnalysisVersionsByRadarEventId(1L)).thenReturn(Collections.<Long, String>emptyMap(),
                Collections.singletonMap(7L, "RULES_V2"));
        when(analyzer.analyze(graph, event)).thenReturn(Optional.of(impact));
        when(impacts.upsert(any(IndustryChainEventImpact.class), any(LocalDateTime.class))).thenReturn(true);

        IndustryChainEventService.RefreshSummary first = service.refresh(1L);
        IndustryChainEventService.RefreshSummary second = service.refresh(1L);

        assertEquals(1, first.getAdded());
        assertEquals(0, first.getSkipped());
        assertEquals(0, second.getAdded());
        assertEquals(1, second.getSkipped());
        verify(analyzer, times(1)).analyze(graph, event);
        verify(impacts, times(1)).upsert(any(IndustryChainEventImpact.class), any(LocalDateTime.class));
    }

    @Test
    void refreshReanalyzesRelationshipsCreatedByAnOlderRuleVersion() {
        IndustryChainRepository chains = mock(IndustryChainRepository.class);
        IndustryChainEventImpactRepository impacts = mock(IndustryChainEventImpactRepository.class);
        RadarRepository radar = mock(RadarRepository.class);
        IndustryChainEventAnalyzer analyzer = mock(IndustryChainEventAnalyzer.class);
        IndustryChainEventService service = new IndustryChainEventService();
        ReflectionTestUtils.setField(service, "chainRepository", chains);
        ReflectionTestUtils.setField(service, "impactRepository", impacts);
        ReflectionTestUtils.setField(service, "radarRepository", radar);
        ReflectionTestUtils.setField(service, "analyzer", analyzer);
        IndustryChainGraph graph = new IndustryChainGraph();
        graph.setChainId(1L);
        RadarEvent event = new RadarEvent();
        event.setId(7L);
        IndustryChainEventImpact impact = new IndustryChainEventImpact();
        impact.setRadarEventId(7L);
        when(chains.findPublishedGraph(1L)).thenReturn(Optional.of(graph));
        when(radar.findEventsSince(any(LocalDateTime.class), anyInt())).thenReturn(Collections.singletonList(event));
        when(analyzer.getAnalysisVersion()).thenReturn("RULES_V2");
        when(impacts.findAnalysisVersionsByRadarEventId(1L)).thenReturn(Collections.singletonMap(7L, "RULES_V1"));
        when(analyzer.analyze(graph, event)).thenReturn(Optional.of(impact));
        when(impacts.upsert(any(IndustryChainEventImpact.class), any(LocalDateTime.class))).thenReturn(false);

        IndustryChainEventService.RefreshSummary summary = service.refresh(1L);

        assertEquals(0, summary.getAdded());
        assertEquals(1, summary.getUpdated());
        assertEquals(0, summary.getSkipped());
    }
}
