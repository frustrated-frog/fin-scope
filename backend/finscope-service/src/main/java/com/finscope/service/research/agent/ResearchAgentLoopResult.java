package com.finscope.service.research.agent;

public class ResearchAgentLoopResult {
    private final boolean finishAccepted;
    private final boolean aborted;
    private final int decisionCount;
    private final int externalActionCount;
    private final String terminationReason;

    private ResearchAgentLoopResult(boolean finishAccepted,
                                    boolean aborted,
                                    int decisionCount,
                                    int externalActionCount,
                                    String terminationReason) {
        this.finishAccepted = finishAccepted;
        this.aborted = aborted;
        this.decisionCount = decisionCount;
        this.externalActionCount = externalActionCount;
        this.terminationReason = terminationReason;
    }

    public static ResearchAgentLoopResult finished(int decisions, int externalActions) {
        return new ResearchAgentLoopResult(true, false, decisions, externalActions, null);
    }

    public static ResearchAgentLoopResult aborted(int decisions, int externalActions, String reason) {
        return new ResearchAgentLoopResult(false, true, decisions, externalActions, reason);
    }

    public boolean isFinishAccepted() { return finishAccepted; }
    public boolean isAborted() { return aborted; }
    public int getDecisionCount() { return decisionCount; }
    public int getExternalActionCount() { return externalActionCount; }
    public String getTerminationReason() { return terminationReason; }
}
