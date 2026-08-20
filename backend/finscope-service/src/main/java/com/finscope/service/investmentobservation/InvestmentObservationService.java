package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.common.exception.BizErrorCode;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.investmentobservation.InvestmentObservationRepository;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationDetail;
import com.finscope.domain.investmentobservation.InvestmentObservationRefreshResult;
import com.finscope.domain.investmentobservation.InvestmentObservationWorkspace;
import com.finscope.domain.radar.RadarEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class InvestmentObservationService {
    private static final int REFRESH_LIMIT = 20;
    private static final int TRANSITION_LIMIT = 20;

    @Resource
    private InvestmentObservationRepository repository;
    @Resource
    private InvestmentObservationCandidateService candidates;
    @Resource
    private InvestmentObservationScoringService scoring;
    @Resource
    private InvestmentObservationLifecycleService lifecycle;
    private Clock clock = Clock.systemDefaultZone();

    public InvestmentObservationRefreshResult refresh() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<InvestmentObservation> existing = repository.findAll();
        List<RadarEvent> sourceCandidates = candidates.load();
        InvestmentObservationRefreshResult result = new InvestmentObservationRefreshResult();
        result.setScannedCount(sourceCandidates.size());
        result.setPreservedCount(existing.size());
        result.setRefreshedAt(now);
        if (sourceCandidates.isEmpty()) {
            applyCounts(result, existing);
            return result;
        }

        List<InvestmentObservation> generated = new ArrayList<InvestmentObservation>();
        for (RadarEvent candidate : sourceCandidates) {
            generated.add(scoring.score(candidate));
        }
        generated.sort(Comparator.comparingInt(InvestmentObservation::getScore).reversed());
        if (generated.size() > REFRESH_LIMIT) {
            generated = new ArrayList<InvestmentObservation>(generated.subList(0, REFRESH_LIMIT));
        }
        scoring.applyFocusFloor(generated);

        List<InvestmentObservation> updated = new ArrayList<InvestmentObservation>();
        for (InvestmentObservation observation : generated) {
            updated.add(repository.upsertGenerated(observation, now));
        }
        result.setUpdatedCount(updated.size());
        applyCounts(result, updated);
        return result;
    }

    public InvestmentObservationWorkspace workspace() {
        LocalDateTime now = LocalDateTime.now(clock);
        return lifecycle.workspace(repository.findAll(), repository.findRecentTransitions(TRANSITION_LIMIT), now);
    }

    public InvestmentObservationDetail detail(Long id) {
        InvestmentObservationDetail result = new InvestmentObservationDetail();
        result.setObservation(requireObservation(id));
        result.setTransitions(repository.findTransitions(id));
        return result;
    }

    public InvestmentObservation updateDisposition(Long id, InvestmentObservationDisposition disposition, int revision) {
        requireObservation(id);
        boolean updated = repository.updateDisposition(id, disposition, revision, LocalDateTime.now(clock));
        if (!updated) {
            throw new BusinessException(BizErrorCode.INVESTMENT_OBSERVATION_STATE_CHANGED);
        }
        return requireObservation(id);
    }

    public InvestmentObservation archive(Long id, int revision, String reason) {
        requireObservation(id);
        boolean updated = repository.archive(id, revision, defaultReason(reason), LocalDateTime.now(clock));
        if (!updated) {
            throw new BusinessException(BizErrorCode.INVESTMENT_OBSERVATION_STATE_CHANGED);
        }
        return requireObservation(id);
    }

    private InvestmentObservation requireObservation(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(BizErrorCode.INVESTMENT_OBSERVATION_NOT_FOUND));
    }

    private void applyCounts(InvestmentObservationRefreshResult result, List<InvestmentObservation> observations) {
        for (InvestmentObservation observation : observations) {
            if (observation.getStage() == InvestmentObservationStage.FOCUS) {
                result.setFocusCount(result.getFocusCount() + 1);
            } else if (observation.getStage() == InvestmentObservationStage.TRACKING) {
                result.setTrackingCount(result.getTrackingCount() + 1);
            } else if (observation.getStage() == InvestmentObservationStage.LEARNING) {
                result.setLearningCount(result.getLearningCount() + 1);
            }
        }
    }

    private String defaultReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "用户将观察对象移出当前研究池";
        }
        return reason.trim();
    }
}
