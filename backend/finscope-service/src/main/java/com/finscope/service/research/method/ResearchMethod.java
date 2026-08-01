package com.finscope.service.research.method;

import com.finscope.service.research.mission.ResearchPlanningInput;

public interface ResearchMethod {
    ResearchMethodDefinition definition();

    boolean supports(ResearchPlanningInput input);
}
