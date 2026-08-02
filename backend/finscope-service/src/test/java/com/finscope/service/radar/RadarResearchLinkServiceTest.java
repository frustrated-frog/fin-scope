package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.research.ResearchRun;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RadarResearchLinkServiceTest {
    @Test
    void linksKnownEventAndResearchRunIdempotently() {
        RadarEventWorkspaceRepository links = mock(RadarEventWorkspaceRepository.class);
        RadarRepository radar = mock(RadarRepository.class); ResearchRunRepository runs = mock(ResearchRunRepository.class);
        RadarResearchLinkService service = new RadarResearchLinkService(links, radar, runs);
        RadarEvent event = new RadarEvent(); event.setId(10L); ResearchRun run = new ResearchRun(); run.setId(41L);
        when(radar.findEvent(10L)).thenReturn(Optional.of(event)); when(runs.findById(41L)).thenReturn(Optional.of(run));
        RadarEventWorkspace.ResearchLink linked = new RadarEventWorkspace.ResearchLink(); linked.setEventId(10L); linked.setResearchRunId(41L);
        when(links.linkResearchRun(10L, 41L, "电池订单是否落地？")).thenReturn(linked);

        assertEquals(41L, service.link(10L, 41L, "电池订单是否落地？").getResearchRunId());
        verify(links).linkResearchRun(10L, 41L, "电池订单是否落地？");
    }

    @Test
    void rejectsUnknownRun() {
        RadarEventWorkspaceRepository links = mock(RadarEventWorkspaceRepository.class);
        RadarRepository radar = mock(RadarRepository.class); ResearchRunRepository runs = mock(ResearchRunRepository.class);
        RadarResearchLinkService service = new RadarResearchLinkService(links, radar, runs);
        RadarEvent event = new RadarEvent(); event.setId(10L); when(radar.findEvent(10L)).thenReturn(Optional.of(event));
        when(runs.findById(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.link(10L, 99L, "问题"));
    }
}
