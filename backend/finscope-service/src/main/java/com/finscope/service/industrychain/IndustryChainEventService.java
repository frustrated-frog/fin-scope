package com.finscope.service.industrychain;

import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.industrychain.IndustryChainEventImpactRepository;
import com.finscope.dao.industrychain.IndustryChainRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.industrychain.IndustryChainEventFeed;
import com.finscope.domain.industrychain.IndustryChainEventImpact;
import com.finscope.domain.industrychain.IndustryChainGraph;
import com.finscope.domain.industrychain.IndustryChainNode;
import com.finscope.domain.radar.RadarEvent;
import lombok.Data;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 复用 Research Radar 事件生成产业链新闻影响视图。 */
@Service
public class IndustryChainEventService {
    private static final int BACKFILL_DAYS = 30;
    private static final int EVENT_LIMIT = 200;

    @Resource
    private IndustryChainRepository chainRepository;
    @Resource
    private IndustryChainEventImpactRepository impactRepository;
    @Resource
    private RadarRepository radarRepository;
    @Resource
    private IndustryChainEventAnalyzer analyzer;

    public RefreshSummary refresh(Long chainId) {
        IndustryChainGraph graph = requiredGraph(chainId);
        LocalDateTime now = LocalDateTime.now();
        List<RadarEvent> events = radarRepository.findEventsSince(now.minusDays(BACKFILL_DAYS), EVENT_LIMIT);
        Map<Long, String> existingVersions = impactRepository.findAnalysisVersionsByRadarEventId(chainId);
        String analysisVersion = analyzer.getAnalysisVersion();
        int added = 0;
        int updated = 0;
        int skipped = 0;
        for (RadarEvent event : events) {
            if (analysisVersion.equals(existingVersions.get(event.getId()))) {
                skipped++;
                continue;
            }
            Optional<IndustryChainEventImpact> analyzed = analyzer.analyze(graph, event);
            if (analyzed.isEmpty()) {
                skipped++;
                continue;
            }
            IndustryChainEventImpact impact = analyzed.get();
            impact.setChainId(chainId);
            if (impactRepository.upsert(impact, now)) {
                added++;
            } else {
                updated++;
            }
        }
        return new RefreshSummary(events.size(), added, updated, skipped, now);
    }

    public IndustryChainEventFeed feed(Long chainId, int hours) {
        int normalizedHours = Math.max(1, Math.min(hours, 720));
        if (impactRepository.countByChainId(chainId) == 0) {
            refresh(chainId);
        }
        IndustryChainGraph graph = requiredGraph(chainId);
        LocalDateTime now = LocalDateTime.now();
        List<RadarEvent> radarEvents = radarRepository.findEventsSince(now.minusHours(normalizedHours), EVENT_LIMIT);
        Map<Long, RadarEvent> eventsById = new HashMap<Long, RadarEvent>();
        for (RadarEvent event : radarEvents) {
            eventsById.put(event.getId(), event);
        }
        Set<String> validNodeKeys = new HashSet<String>();
        for (IndustryChainNode node : graph.getNodes()) {
            validNodeKeys.add(node.getNodeKey());
        }
        List<IndustryChainEventFeed.EventItem> items = new ArrayList<IndustryChainEventFeed.EventItem>();
        Map<String, Integer> nodeCounts = new LinkedHashMap<String, Integer>();
        for (IndustryChainEventImpact impact : impactRepository.findByChainId(chainId)) {
            RadarEvent event = eventsById.get(impact.getRadarEventId());
            if (event == null || !isVisible(event) || !validNodeKeys.contains(impact.getDirectNodeKey())) {
                continue;
            }
            impact.setPathNodeKeys(validPath(impact.getPathNodeKeys(), validNodeKeys));
            items.add(item(event, impact));
            for (String nodeKey : impact.getPathNodeKeys()) {
                nodeCounts.put(nodeKey, nodeCounts.getOrDefault(nodeKey, 0) + 1);
            }
        }
        items.sort((left, right) -> compare(right.getLastSeenAt(), left.getLastSeenAt()));
        IndustryChainEventFeed feed = new IndustryChainEventFeed();
        feed.setChainId(chainId);
        feed.setHours(normalizedHours);
        feed.setRefreshedAt(now);
        feed.setNodeEventCounts(nodeCounts);
        feed.setEvents(items);
        return feed;
    }

    private boolean isVisible(RadarEvent event) {
        return "ACTIVE".equals(event.getStatus()) || "QUIET".equals(event.getStatus());
    }

    private IndustryChainGraph requiredGraph(Long chainId) {
        return chainRepository.findPublishedGraph(chainId)
                .orElseThrow(() -> new ResourceNotFoundException("产业链图谱尚未生成"));
    }

    private List<String> validPath(List<String> path, Set<String> validNodeKeys) {
        List<String> result = new ArrayList<String>();
        for (String nodeKey : path) {
            if (validNodeKeys.contains(nodeKey)) result.add(nodeKey);
        }
        return result;
    }

    private IndustryChainEventFeed.EventItem item(RadarEvent event, IndustryChainEventImpact impact) {
        IndustryChainEventFeed.EventItem item = new IndustryChainEventFeed.EventItem();
        item.setEventId(event.getId());
        item.setTitle(event.getCanonicalTitle());
        item.setSummary(event.getSummary());
        item.setCategoryCode(event.getCategoryCode());
        item.setStatus(event.getStatus());
        item.setFirstSeenAt(event.getFirstSeenAt());
        item.setLastSeenAt(event.getLastSeenAt());
        item.setSourceCount(event.getSourceCount());
        item.setSignalCount(event.getSignalCount());
        item.setHotspotScore(event.getHotspotScore());
        item.setImpact(impact);
        return item;
    }

    private int compare(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    @Data
    public static final class RefreshSummary {
        private final int scanned;
        private final int added;
        private final int updated;
        private final int skipped;
        private final LocalDateTime refreshedAt;
    }
}
