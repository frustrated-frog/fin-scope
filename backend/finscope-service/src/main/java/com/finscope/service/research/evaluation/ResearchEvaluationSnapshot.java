package com.finscope.service.research.evaluation;

import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;
import com.finscope.domain.research.runtime.ResearchRuntimeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchEvaluationSnapshot {
    private final ResearchRun run;
    private final ResearchReport report;
    private final ResearchRuntimeCheckpoint checkpoint;
    private final List<ResearchRuntimeEvent> events;
    private final int actualEvidenceCount;
    private final int actualSourceCount;

    public ResearchEvaluationSnapshot(ResearchRun run, ResearchReport report,
                                      ResearchRuntimeCheckpoint checkpoint, List<ResearchRuntimeEvent> events) {
        this(run, report, checkpoint, events, 0, 0);
    }

    public ResearchEvaluationSnapshot(ResearchRun run, ResearchReport report,
                                      ResearchRuntimeCheckpoint checkpoint, List<ResearchRuntimeEvent> events,
                                      int actualEvidenceCount, int actualSourceCount) {
        this.run = run;
        this.report = report;
        this.checkpoint = checkpoint;
        this.events = events == null ? Collections.<ResearchRuntimeEvent>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchRuntimeEvent>(events));
        this.actualEvidenceCount = actualEvidenceCount;
        this.actualSourceCount = actualSourceCount;
    }

    public ResearchRun getRun() { return run; }
    public ResearchReport getReport() { return report; }
    public ResearchRuntimeCheckpoint getCheckpoint() { return checkpoint; }
    public List<ResearchRuntimeEvent> getEvents() { return events; }
    public int getActualEvidenceCount() { return actualEvidenceCount; }
    public int getActualSourceCount() { return actualSourceCount; }
}
