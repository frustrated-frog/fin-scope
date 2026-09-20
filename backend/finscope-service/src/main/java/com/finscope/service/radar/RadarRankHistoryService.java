package com.finscope.service.radar;

import com.finscope.dao.radar.RadarRankHistoryRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarRankPoint;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RadarRankHistoryService {
    @Resource
    private RadarRankHistoryRepository history;
    @Resource
    private RadarRepository radar;

    public void record(List<RadarEvent> events, LocalDateTime now) {
        List<RadarEvent> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparingInt(RadarEvent::getPriorityScore).reversed()
                .thenComparing(Comparator.comparingInt(RadarEvent::getHotspotScore).reversed())
                .thenComparing(RadarEvent::getEventKey));
        LocalDateTime bucket = now.withMinute(now.getMinute() < 30 ? 0 : 30).withSecond(0).withNano(0);
        List<RadarRankPoint> batch = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            RadarEvent event = ordered.get(index);
            RadarRankPoint point = new RadarRankPoint();
            point.setEventKey(event.getEventKey());
            point.setObservedAt(bucket);
            point.setRankPosition(index + 1);
            point.setHotspotScore(event.getHotspotScore());
            point.setReportCount(event.getSignalCount());
            point.setSourceCount(event.getSourceCount());
            batch.add(point);
            if (batch.size() == 250) {
                history.save(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            history.save(batch);
        }
        history.prune(now.minusDays(7));
    }

    public List<RadarRankPoint> history(long eventId) {
        return radar.findEvent(eventId).map(event -> history.history(event.getEventKey(), LocalDateTime.now().minusDays(7)))
                .orElseGet(Collections::emptyList);
    }
}
