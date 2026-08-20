package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.InvestmentObservationDisposition;
import com.finscope.common.enums.investmentobservation.InvestmentObservationStage;
import com.finscope.domain.investmentobservation.InvestmentObservation;
import com.finscope.domain.investmentobservation.InvestmentObservationTransition;
import com.finscope.domain.investmentobservation.InvestmentObservationWorkspace;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvestmentObservationLifecycleService {
    private static final int MAX_FOCUS = 5;
    private static final int MAX_ACTIVE = 20;

    public InvestmentObservationWorkspace workspace(List<InvestmentObservation> observations,
                                                    List<InvestmentObservationTransition> transitions,
                                                    LocalDateTime now) {
        InvestmentObservationWorkspace result = new InvestmentObservationWorkspace();
        int visibleActive = 0;
        int archivedCount = 0;
        LocalDate today = now.toLocalDate();
        for (InvestmentObservation observation : observations) {
            if (observation.getDisposition() == InvestmentObservationDisposition.IGNORED) {
                continue;
            }
            if (observation.getStage() == InvestmentObservationStage.ARCHIVED) {
                result.getArchived().add(observation);
                archivedCount++;
                continue;
            }
            if (visibleActive >= MAX_ACTIVE) {
                continue;
            }
            if (observation.getStage() == InvestmentObservationStage.FOCUS) {
                if (result.getFocus().size() >= MAX_FOCUS) {
                    result.getTracking().add(observation);
                } else {
                    result.getFocus().add(observation);
                }
            } else if (observation.getStage() == InvestmentObservationStage.TRACKING) {
                result.getTracking().add(observation);
            } else {
                result.getLearning().add(observation);
            }
            visibleActive++;
            if (observation.getLastChangedAt() != null
                    && today.equals(observation.getLastChangedAt().toLocalDate())) {
                result.setChangedTodayCount(result.getChangedTodayCount() + 1);
            }
            if (observation.isEvidenceInsufficient() || hasText(observation.getNextValidation())) {
                result.setWaitingValidationCount(result.getWaitingValidationCount() + 1);
            }
        }
        result.setActiveCount(visibleActive);
        result.setArchivedCount(archivedCount);
        result.setTransitions(transitions);
        result.setRefreshedAt(now);
        if (!result.getFocus().isEmpty() && allEvidenceInsufficient(result.getFocus())) {
            result.setWarning("当前焦点是相对优先对象，证据尚未达到高置信门槛");
        }
        return result;
    }

    private boolean allEvidenceInsufficient(List<InvestmentObservation> values) {
        for (InvestmentObservation value : values) {
            if (!value.isEvidenceInsufficient()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
