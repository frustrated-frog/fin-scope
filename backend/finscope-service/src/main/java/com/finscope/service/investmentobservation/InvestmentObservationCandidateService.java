package com.finscope.service.investmentobservation;

import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class InvestmentObservationCandidateService {
    private static final int CANDIDATE_LIMIT = 50;

    @Resource
    private MajorEventRepository majorEvents;

    public List<RadarEvent> load() {
        List<MajorEvent> archived = majorEvents.find(null, null, null, null);
        List<RadarEvent> result = new ArrayList<RadarEvent>();
        for (MajorEvent value : archived) {
            if (result.size() >= CANDIDATE_LIMIT) {
                break;
            }
            if (!"NEWS_ITEM".equals(value.getOriginType()) && !"RADAR_EVENT".equals(value.getOriginType())) {
                continue;
            }
            RadarEvent candidate = fromMajorEvent(value);
            if (isInvestmentLearningCandidate(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private RadarEvent fromMajorEvent(MajorEvent value) {
        RadarEvent event = new RadarEvent();
        event.setId(value.getId());
        event.setEventKey(value.getOriginType() + ':' + value.getOriginKey());
        event.setCanonicalTitle(value.getTitle());
        event.setSummary(value.getSummary());
        event.setCategoryCode(value.getCategoryCode());
        event.setDashboardCategory(value.getCategoryCode());
        event.setStatus("ACTIVE");
        if (value.getOccurredDate() != null) {
            event.setFirstSeenAt(value.getOccurredDate().atStartOfDay());
            event.setLastSeenAt(value.getOccurredDate().atStartOfDay());
        }
        event.setSourceCount(1);
        event.setSignalCount(1);
        event.setConfidenceScore(100);
        event.setEvidenceCount(1);
        event.setEvidenceSourceCount(1);
        event.setNextObservation("在大事记中补充后续事实与验证结果");
        event.setUpdatedAt(value.getUpdatedAt());
        return event;
    }

    private boolean isInvestmentLearningCandidate(RadarEvent candidate) {
        String title = candidate.getCanonicalTitle() == null ? "" : candidate.getCanonicalTitle();
        return !containsAny(title, "涉嫌受贿", "被提起公诉", "接受纪律审查", "接受监察调查", "被开除党籍",
                "客机“冒黑烟”", "客机冒黑烟");
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
