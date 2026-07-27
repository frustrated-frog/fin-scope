package com.finscope.service.research.agent.tool;

import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.domain.research.mission.ResearchToolDescriptor;

import java.util.Map;

public interface ResearchAgentTool {
    ResearchToolDescriptor descriptor();

    void validate(Map<String, Object> arguments);

    ResearchToolObservation execute(ResearchAgentToolContext context, Map<String, Object> arguments);
}
