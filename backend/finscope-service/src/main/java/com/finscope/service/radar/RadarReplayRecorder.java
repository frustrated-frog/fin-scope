package com.finscope.service.radar;

import com.finscope.dao.radar.RadarReplayRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarReplayItem;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class RadarReplayRecorder {
    @Resource
    private RadarReplayRepository repository;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public List<RadarReplayItem> capture(List<RadarEvent> events) {
        List<RadarReplayItem> result = new ArrayList<>();
        for (RadarEvent event : events) {
            if (event.getId() == null || event.getEventKey() == null) {
                continue;
            }
            RadarReplayItem item = new RadarReplayItem();
            item.setEventId(event.getId().toString());
            item.setEventKey(event.getEventKey());
            item.setTitle(event.getCanonicalTitle());
            item.setSummary(event.getSummary());
            item.setCategory(category(event.getDashboardCategory()));
            item.setSourceCount(event.getSourceCount());
            item.setReportCount(event.getSignalCount());
            result.add(item);
        }
        return result;
    }

    public void record(String runKey, List<RadarReplayItem> observed, ResearchRadarView published) {
        Map<String, String> categories = new HashMap<>();
        for (RadarReplayItem item : observed) {
            categories.put(item.getEventId(), item.getCategory());
        }
        List<RadarReplayItem> ranking = new ArrayList<>();
        for (ResearchRadarView.EventCard card : published.getEvents()) {
            RadarReplayItem item = new RadarReplayItem();
            item.setEventId(card.getId().toString());
            item.setEventKey(card.getEventKey());
            item.setTitle(card.getTitle());
            item.setSummary(card.getSummary());
            item.setCategory(categories.getOrDefault(item.getEventId(), "OTHER"));
            item.setHotspotScore(card.getHotspotScore());
            item.setSourceCount(card.getSourceCount());
            item.setReportCount(card.getSignalCount());
            item.setLifecycle(card.getHotspotLifecycleState());
            ranking.add(item);
        }
        repository.savePublished(runKey, LocalDateTime.now(clock), observed, ranking);
    }

    private String category(String value) {
        return Set.of("FINANCE", "TECHNOLOGY", "POLITICS").contains(value == null ? "" : value) ? value : "OTHER";
    }
}
