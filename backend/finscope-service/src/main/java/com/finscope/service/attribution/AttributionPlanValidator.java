package com.finscope.service.attribution;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.StringUtils;
import org.springframework.stereotype.Component;
import com.finscope.common.exception.BizErrorCode;

@Component
public class AttributionPlanValidator {
    public void validate(AttributionResearchPlan plan) {
        if (plan == null || plan.getBudget() == null || plan.getTracks().isEmpty()) {
            throw new BusinessException(BizErrorCode.ATTRIBUTION_PLAN_REQUIRED);
        }
        int queryCount = 0;
        boolean hasCounter = false;
        for (AttributionResearchPlan.Track track : plan.getTracks()) {
            if (StringUtils.isBlank(track.getCode()) || StringUtils.isBlank(track.getSuccessCriteria()) || track.getQueries().isEmpty()) {
                throw new BusinessException(BizErrorCode.ATTRIBUTION_TRACK_CONTRACT_MISSING);
            }
            if (track.getMaxQueries() <= 0 || track.getMaxQueries() > plan.getBudget().getMaxQueriesPerTrack()) {
                throw new BusinessException(BizErrorCode.ATTRIBUTION_TRACK_QUERY_BUDGET_INVALID);
            }
            queryCount += track.getMaxQueries();
            hasCounter = hasCounter || "COUNTER".equals(track.getCode());
        }
        if (!hasCounter || queryCount > plan.getBudget().getMaxQueries()) {
            throw new BusinessException(BizErrorCode.ATTRIBUTION_PLAN_BUDGET_INVALID);
        }
    }
}
