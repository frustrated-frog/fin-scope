package com.finscope.service.investmentobservation;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class InvestmentObservationCandidateService {
    private static final int CANDIDATE_LIMIT = 50;

    @Resource
    private RadarRepository radarRepository;

    public List<RadarEvent> load() {
        List<RadarEvent> candidates = radarRepository.findObservationCandidates(CANDIDATE_LIMIT);
        List<RadarEvent> result = new ArrayList<RadarEvent>();
        for (RadarEvent candidate : candidates) {
            if (isInvestmentLearningCandidate(candidate)) {
                result.add(candidate);
            }
        }
        return result;
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
