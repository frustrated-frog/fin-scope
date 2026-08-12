package com.finscope.service.radar;

import com.finscope.domain.radar.RadarEventStatus;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
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
import java.util.Map;

/** 只消费本轮生产结果，将可点击的页面列表一次性物化为 Redis 快照。 */
@Service
public class RadarSnapshotProjectionService {
    public static final String DEFAULT_RADAR_VARIANT = "category=ALL&watchlist=false&limit=20&state=ALL";
    public static final String HOTSPOT_VARIANT = "hotspots";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ViewSnapshotCacheService snapshots;
    private final ViewRevisionService revisions;
    private final DashboardHotspotRankingService rankings;
    private final RadarEventInterpretationService interpretations;
    private final RadarEventWorkspaceService workspace;
    private volatile List<RadarEvent> latestEvents = Collections.emptyList();
    private volatile RadarRefreshRun latestRun;

    public RadarSnapshotProjectionService(ViewSnapshotCacheService snapshots, ViewRevisionService revisions,
                                          DashboardHotspotRankingService rankings,
                                          RadarEventInterpretationService interpretations,
                                          RadarEventWorkspaceService workspace) {
        this.snapshots = snapshots;
        this.revisions = revisions;
        this.rankings = rankings;
        this.interpretations = interpretations;
        this.workspace = workspace;
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
        return new ResearchRadarView(cards(ranked, 20), Collections.emptyList(),
                Collections.emptyList(), run.getCompletedAt(), ResearchRadarView.ProductionStatus.of(false,
                run.getStatus(), run.getCompletedAt(), run.getSourceCount(), run.getSignalCount(),
                run.getEventCount(), run.getWarning()));
    }

    private List<ResearchRadarView.EventCard> cards(List<RadarEvent> events, int limit) {
        List<RadarEvent> selected = events.subList(0, Math.min(limit, events.size()));
        List<Long> selectedIds = new ArrayList<Long>();
        for (RadarEvent event : selected) {
            if (event.getId() != null) {
                selectedIds.add(event.getId());
            }
        }
        Map<Long, RadarEventInterpretation> latest = latestInterpretations(selectedIds);
        Map<Long, RadarEventWorkspace.Summary> summaries = workspaceSummaries(events);
        List<ResearchRadarView.EventCard> cards = new ArrayList<ResearchRadarView.EventCard>();
        for (RadarEvent event : selected) {
            RadarEventWorkspace.Summary summary = summaries.get(event.getId());
            cards.add(new ResearchRadarView.EventCard(event, latest.get(event.getId()), summary));
        }
        return cards;
    }

    private Map<Long, RadarEventInterpretation> latestInterpretations(List<Long> eventIds) {
        if (interpretations == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return interpretations.latestByEventIds(eventIds);
        } catch (RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private Map<Long, RadarEventWorkspace.Summary> workspaceSummaries(List<RadarEvent> events) {
        if (workspace == null || events.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = new ArrayList<Long>();
        for (RadarEvent event : events) {
            if (event.getId() != null) {
                ids.add(event.getId());
            }
        }
        try {
            Map<Long, RadarEventWorkspace.Summary> summaries = workspace.summaries(ids);
            for (RadarEvent event : events) {
                workspace.reconcileRead(event, summaries.get(event.getId()));
            }
            workspace.createChangeNotifications(events, summaries);
            return summaries;
        } catch (RuntimeException ignored) {
            return Collections.emptyMap();
        }
    }

    private List<RadarEvent> activeEvents(List<RadarEvent> events) {
        List<RadarEvent> values = new ArrayList<RadarEvent>();
        if (events == null) return values;
        for (RadarEvent event : events) {
            RadarEventStatus status = RadarEventStatus.from(event == null ? null : event.getStatus());
            if (status == RadarEventStatus.ACTIVE || status == RadarEventStatus.QUIET) values.add(event);
        }
        return values;
    }
}
