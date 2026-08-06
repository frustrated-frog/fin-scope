package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.service.cache.ViewRevisionService;
import com.finscope.service.cache.ViewSnapshotCacheService;
import com.finscope.service.dashboard.DashboardHotspotRankingService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 只消费本轮生产结果，将可点击的页面列表一次性物化为 Redis 快照。 */
@Service
public class RadarSnapshotProjectionService {
    public static final String DEFAULT_RADAR_VARIANT = "category=ALL&watchlist=false&limit=20&state=ALL";
    public static final String HOTSPOT_VARIANT = "hotspots";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ViewSnapshotCacheService snapshots;
    private final ViewRevisionService revisions;
    private final DashboardHotspotRankingService rankings;
    private volatile List<RadarEvent> latestEvents = Collections.emptyList();
    private volatile RadarRefreshRun latestRun;

    public RadarSnapshotProjectionService(ViewSnapshotCacheService snapshots, ViewRevisionService revisions,
                                          DashboardHotspotRankingService rankings) {
        this.snapshots = snapshots;
        this.revisions = revisions;
        this.rankings = rankings;
    }

    public synchronized boolean prewarm(List<RadarEvent> events, RadarRefreshRun run) {
        if (run == null) return false;
        long radarRevision = snapshots.nextRevision("radar");
        long dashboardRevision = snapshots.nextRevision("dashboard");
        if (radarRevision <= 0 || dashboardRevision <= 0) return false;
        List<RadarEvent> values = activeEvents(events);
        boolean radarWritten = snapshots.write("radar", radarRevision, DEFAULT_RADAR_VARIANT,
                radarView(values, run), TTL);
        boolean dashboardWritten = snapshots.write("dashboard", dashboardRevision, HOTSPOT_VARIANT,
                rankings.rankings(values), TTL);
        if (!radarWritten || !dashboardWritten) return false;
        LocalDateTime completedAt = run.getCompletedAt();
        revisions.publish("radar", radarRevision, completedAt);
        revisions.publish("dashboard", dashboardRevision, completedAt);
        latestEvents = new ArrayList<RadarEvent>(values);
        latestRun = run;
        return true;
    }

    /** Agent 增强修改了本轮内存事件后，重建完整快照并发布下一版本。 */
    public synchronized boolean republish() {
        return latestRun != null && prewarm(latestEvents, latestRun);
    }

    private ResearchRadarView radarView(List<RadarEvent> events, RadarRefreshRun run) {
        List<RadarEvent> ranked = new ArrayList<RadarEvent>(events);
        ranked.sort(Comparator.comparingInt(RadarEvent::getPriorityScore).reversed()
                .thenComparing(RadarEvent::getHotspotScore, Comparator.reverseOrder())
                .thenComparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
        List<RadarEvent> latest = new ArrayList<RadarEvent>(ranked);
        latest.sort(Comparator.comparing(RadarEvent::getLastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return new ResearchRadarView(cards(ranked, 20), cards(latest, 5), Collections.emptyList(),
                Collections.emptyList(), run.getCompletedAt(), ResearchRadarView.ProductionStatus.of(false,
                run.getStatus(), run.getCompletedAt(), run.getSourceCount(), run.getSignalCount(),
                run.getEventCount(), run.getWarning()));
    }

    private List<ResearchRadarView.EventCard> cards(List<RadarEvent> events, int limit) {
        List<ResearchRadarView.EventCard> cards = new ArrayList<ResearchRadarView.EventCard>();
        for (RadarEvent event : events.subList(0, Math.min(limit, events.size()))) cards.add(new ResearchRadarView.EventCard(event));
        return cards;
    }

    private List<RadarEvent> activeEvents(List<RadarEvent> events) {
        List<RadarEvent> values = new ArrayList<RadarEvent>();
        if (events == null) return values;
        for (RadarEvent event : events) {
            if (event != null && ("ACTIVE".equalsIgnoreCase(event.getStatus()) || "QUIET".equalsIgnoreCase(event.getStatus()))) values.add(event);
        }
        return values;
    }
}
