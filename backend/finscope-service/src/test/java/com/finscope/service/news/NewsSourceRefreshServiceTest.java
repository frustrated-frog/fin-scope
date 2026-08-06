package com.finscope.service.news;

import com.finscope.service.cache.ViewRevisionService;
import com.finscope.service.radar.RadarHotspotRefreshService;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsSourceRefreshServiceTest {
    @Test
    void refreshesSourcesThenInvalidatesNewsAndRequestsRadarProduction() {
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        RadarHotspotRefreshService radar = mock(RadarHotspotRefreshService.class);
        ViewRevisionService revisions = mock(ViewRevisionService.class);
        when(gateway.refreshNewsFlashSources(any())).thenReturn(new ResearchMaterialGatewayResult(
                Collections.emptyList(), Collections.emptyList()));

        NewsSourceRefreshService service = new NewsSourceRefreshService(gateway, radar, revisions, Runnable::run);

        assertTrue(service.requestRefresh());

        verify(gateway).refreshNewsFlashSources(any());
        verify(revisions).invalidate("news");
        verify(radar).requestScheduledRefresh();
    }
}
