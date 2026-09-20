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

        NewsSourceRefreshService service = new NewsSourceRefreshService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "gateway", gateway);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "radarRefresh", radar);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "viewRevisions", revisions);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "executor", (java.util.concurrent.Executor) Runnable::run);
        NewsWindowService window = mock(NewsWindowService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "window", window);

        assertTrue(service.requestRefresh());

        verify(gateway).refreshNewsFlashSources(any());
        var order = org.mockito.Mockito.inOrder(window, revisions, radar);
        order.verify(window).ingest(Collections.emptyList());
        order.verify(revisions).invalidate("news");
        order.verify(radar).requestScheduledRefresh();
    }
}
