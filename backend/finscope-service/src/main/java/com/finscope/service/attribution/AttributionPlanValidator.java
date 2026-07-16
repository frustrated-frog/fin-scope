package com.finscope.service.attribution;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class AttributionPlanValidator {
    public void validate(AttributionResearchPlan plan) {
        if (plan == null || plan.getBudget() == null || plan.getTracks().isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "归因研究计划不能为空");
        }
        int queryCount = 0;
        boolean hasCounter = false;
        for (AttributionResearchPlan.Track track : plan.getTracks()) {
            if (StringUtils.isBlank(track.getCode()) || StringUtils.isBlank(track.getSuccessCriteria()) || track.getQueries().isEmpty()) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "归因研究轨道缺少执行合同");
            }
            if (track.getMaxQueries() <= 0 || track.getMaxQueries() > plan.getBudget().getMaxQueriesPerTrack()) {
                throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "归因研究轨道查询预算不合法");
            }
            queryCount += track.getMaxQueries();
            hasCounter = hasCounter || "COUNTER".equals(track.getCode());
        }
        if (!hasCounter || queryCount > plan.getBudget().getMaxQueries()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "归因研究计划缺少反证轨道或超出总预算");
        }
    }
}
