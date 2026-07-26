package com.finscope.service.research.runtime;

import com.finscope.domain.research.runtime.ResearchRuntimeCheckpoint;

public final class RuntimeNodeStart {
    private final boolean started;
    private final boolean alreadyCompleted;
    private final String terminationReason;
    private final ResearchRuntimeCheckpoint checkpoint;

    private RuntimeNodeStart(boolean started,
                             boolean alreadyCompleted,
                             String terminationReason,
                             ResearchRuntimeCheckpoint checkpoint) {
        this.started = started;
        this.alreadyCompleted = alreadyCompleted;
        this.terminationReason = terminationReason;
        this.checkpoint = checkpoint;
    }

    public static RuntimeNodeStart started(ResearchRuntimeCheckpoint checkpoint) {
        return new RuntimeNodeStart(true, false, null, checkpoint);
    }

    public static RuntimeNodeStart alreadyCompleted() {
        return new RuntimeNodeStart(false, true, null, null);
    }

    public static RuntimeNodeStart terminated(String reason, ResearchRuntimeCheckpoint checkpoint) {
        return new RuntimeNodeStart(false, false, reason, checkpoint);
    }

    public boolean isStarted() { return started; }
    public boolean isAlreadyCompleted() { return alreadyCompleted; }
    public String getTerminationReason() { return terminationReason; }
    public ResearchRuntimeCheckpoint getCheckpoint() { return checkpoint; }
}
