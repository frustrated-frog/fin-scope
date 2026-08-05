package com.finscope.service.radar;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RadarHotspotRefreshScheduler {
    private final RadarHotspotRefreshService refresh;

    public RadarHotspotRefreshScheduler(RadarHotspotRefreshService refresh) { this.refresh = refresh; }

    @Scheduled(initialDelayString = "${finscope.radar.refresh-initial-delay-ms:15000}",
            fixedDelayString = "${finscope.radar.refresh-interval-ms:300000}")
    public void refresh() { refresh.requestScheduledRefresh(); }
}
