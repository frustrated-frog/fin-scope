package com.finscope.domain.radar;

import com.finscope.domain.agent.AgentRun;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/** Event data selected from a single read of the radar cache. */
@Value
@Builder
public class RadarEventDetailSnapshot {
    RadarEvent event;
    @Builder.Default List<RadarSignal> signals = Collections.emptyList();
    @Builder.Default List<RadarEventSignal> signalLinks = Collections.emptyList();
    @Builder.Default List<RadarEvidence> evidence = Collections.emptyList();
    @Builder.Default List<RadarEventInterpretation> interpretations = Collections.emptyList();
    RadarEventInterpretation latestInterpretation;
    RadarEventWorkspace.State workspaceState;
    @Builder.Default List<RadarEventWorkspace.ResearchLink> researchLinks = Collections.emptyList();
    @Builder.Default List<AgentRun> agentRuns = Collections.emptyList();
}
