package com.finscope.service.radar;

import com.finscope.dao.radar.RadarEventWorkspaceRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarSignal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RadarEventTimelineService {
    private final RadarEventWorkspaceRepository repository;
    public RadarEventTimelineService(RadarEventWorkspaceRepository repository) { this.repository = repository; }

    public List<RadarEventWorkspace.TimelineEntry> timeline(RadarEvent event, List<RadarSignal> signals,
                                                            List<RadarEvidence> evidence,
                                                            RadarEventInterpretation interpretation) {
        repository.appendTimeline(event.getId(), "event:" + event.getId() + ":first", "FIRST_SEEN", "事件首次进入雷达",
                event.getCanonicalTitle(), "EVENT", event.getId(), event.getFirstSeenAt());
        if (signals != null) for (RadarSignal signal : signals) {
            repository.appendTimeline(event.getId(), "signal:" + signal.getId(), "SIGNAL", "新增来源消息",
                    text(signal.getTitle(), signal.getContent()), "SIGNAL", signal.getId(), signal.getPublishedAt());
        }
        if (evidence != null) for (RadarEvidence item : evidence) {
            repository.appendTimeline(event.getId(), "evidence:" + item.getId(), "EVIDENCE", "新增研究证据",
                    text(item.getTitle(), item.getSummary()), "EVIDENCE", item.getId(), item.getPublishedAt());
        }
        if (interpretation != null && "SUCCESS".equals(interpretation.getStatus())) {
            repository.appendTimeline(event.getId(), "interpretation:" + interpretation.getId(), "INTERPRETATION", "事件解读已更新",
                    "已基于当前消息与证据生成结构化解读", "INTERPRETATION", interpretation.getId(), interpretation.getCompletedAt());
        }
        return repository.findTimeline(event.getId());
    }

    public void action(Long eventId, String fingerprint, String type, String title, String summary,
                       String referenceType, Long referenceId) {
        repository.appendTimeline(eventId, fingerprint, type, title, summary, referenceType, referenceId, null);
    }

    private String text(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred;
    }
}
