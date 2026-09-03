package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarEventSnapshot;
import com.finscope.domain.radar.RadarEventWorkspace;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.radar.RadarPairDecision;
import com.finscope.domain.radar.RadarRefreshRun;
import com.finscope.domain.radar.RadarRefreshStep;
import com.finscope.domain.radar.RadarSignal;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class RadarCacheState {
    private Map<Long, RadarSignal> signals = new LinkedHashMap<Long, RadarSignal>();
    private Map<String, Long> signalIdsByItemId = new LinkedHashMap<String, Long>();
    private Map<Long, RadarEvent> events = new LinkedHashMap<Long, RadarEvent>();
    private Map<String, Long> eventIdsByKey = new LinkedHashMap<String, Long>();
    private Map<Long, List<RadarEventSignal>> eventSignals = new LinkedHashMap<Long, List<RadarEventSignal>>();
    private Map<Long, List<RadarEventSnapshot>> snapshots = new LinkedHashMap<Long, List<RadarEventSnapshot>>();
    private Map<Long, List<RadarEvidence>> evidence = new LinkedHashMap<Long, List<RadarEvidence>>();
    private Map<Long, List<RadarEventInterpretation>> interpretations = new LinkedHashMap<Long, List<RadarEventInterpretation>>();
    private Map<String, RadarPairDecision> pairDecisions = new LinkedHashMap<String, RadarPairDecision>();
    private Map<Long, RadarRefreshRun> runs = new LinkedHashMap<Long, RadarRefreshRun>();
    private Map<Long, List<RadarRefreshStep>> runSteps = new LinkedHashMap<Long, List<RadarRefreshStep>>();
    private Map<Long, RadarEventWorkspace.State> userStates = new LinkedHashMap<Long, RadarEventWorkspace.State>();
    private Map<Long, List<RadarEventWorkspace.TimelineEntry>> timelines = new LinkedHashMap<Long, List<RadarEventWorkspace.TimelineEntry>>();
    private Map<Long, Set<String>> timelineFingerprints = new LinkedHashMap<Long, Set<String>>();
    private Map<Long, List<RadarEventWorkspace.ResearchLink>> researchLinks = new LinkedHashMap<Long, List<RadarEventWorkspace.ResearchLink>>();
    private List<RadarEventWorkspace.Notification> notifications = new ArrayList<RadarEventWorkspace.Notification>();
    private Set<String> notificationFingerprints = new LinkedHashSet<String>();
    private long nextSequence = 1L;

    public long nextSequence() {
        long value = nextSequence;
        nextSequence++;
        return value;
    }
}
