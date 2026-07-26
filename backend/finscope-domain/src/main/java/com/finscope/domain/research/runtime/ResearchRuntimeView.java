package com.finscope.domain.research.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchRuntimeView {
    private ResearchRuntimeCheckpoint checkpoint;
    private List<ResearchRuntimeEvent> events = Collections.emptyList();
    private boolean recoverable;

    public ResearchRuntimeCheckpoint getCheckpoint() { return checkpoint; }
    public void setCheckpoint(ResearchRuntimeCheckpoint checkpoint) { this.checkpoint = checkpoint; }
    public List<ResearchRuntimeEvent> getEvents() { return events; }
    public void setEvents(List<ResearchRuntimeEvent> events) {
        this.events = events == null ? Collections.<ResearchRuntimeEvent>emptyList()
                : Collections.unmodifiableList(new ArrayList<ResearchRuntimeEvent>(events));
    }
    public boolean isRecoverable() { return recoverable; }
    public void setRecoverable(boolean recoverable) { this.recoverable = recoverable; }
}
