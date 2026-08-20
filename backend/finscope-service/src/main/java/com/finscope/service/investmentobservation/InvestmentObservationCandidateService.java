package com.finscope.service.investmentobservation;

import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class InvestmentObservationCandidateService {
    private static final int CANDIDATE_LIMIT = 50;

    @Resource
    private RadarRepository radarRepository;

    public List<RadarEvent> load() {
        return radarRepository.findObservationCandidates(CANDIDATE_LIMIT);
    }
}
