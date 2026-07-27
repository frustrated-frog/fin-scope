package com.finscope.service.research.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchFinishVerdict {
    private final boolean accepted;
    private final String reasonCode;
    private final List<String> missingConditions;

    public ResearchFinishVerdict(boolean accepted, String reasonCode, List<String> missingConditions) {
        this.accepted = accepted;
        this.reasonCode = reasonCode;
        this.missingConditions = missingConditions == null || missingConditions.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(missingConditions));
    }

    public boolean isAccepted() { return accepted; }
    public String getReasonCode() { return reasonCode; }
    public List<String> getMissingConditions() { return missingConditions; }
}
