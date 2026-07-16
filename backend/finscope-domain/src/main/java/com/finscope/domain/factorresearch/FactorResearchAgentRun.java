package com.finscope.domain.factorresearch;

import com.finscope.domain.agent.AgentRun;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persisted source of truth for one explicitly approved, read-only research-agent run. */
public class FactorResearchAgentRun {
    private Long id;
    private Long datasetId;
    private String datasetFingerprint;
    private FactorIdentity factor;
    private Long researchDraftId;
    private String question;
    private String status;
    private List<String> plan = Collections.emptyList();
    private List<String> allowedTools = Collections.emptyList();
    private int maxToolCalls;
    private int toolCallsUsed;
    private int maxLlmCalls;
    private int llmCallsUsed;
    private int maxRunSeconds;
    private String evidenceJson;
    private String evidenceHash;
    private String findingJson;
    private String stopReason;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private List<AgentRun> trace = Collections.emptyList();

    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public Long getDatasetId() { return datasetId; } public void setDatasetId(Long value) { datasetId = value; }
    public String getDatasetFingerprint() { return datasetFingerprint; } public void setDatasetFingerprint(String value) { datasetFingerprint = value; }
    public FactorIdentity getFactor() { return factor; } public void setFactor(FactorIdentity value) { factor = value; }
    public Long getResearchDraftId() { return researchDraftId; } public void setResearchDraftId(Long value) { researchDraftId = value; }
    public String getQuestion() { return question; } public void setQuestion(String value) { question = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public List<String> getPlan() { return plan; } public void setPlan(List<String> value) { plan = immutable(value); }
    public List<String> getAllowedTools() { return allowedTools; } public void setAllowedTools(List<String> value) { allowedTools = immutable(value); }
    public int getMaxToolCalls() { return maxToolCalls; } public void setMaxToolCalls(int value) { maxToolCalls = value; }
    public int getToolCallsUsed() { return toolCallsUsed; } public void setToolCallsUsed(int value) { toolCallsUsed = value; }
    public int getMaxLlmCalls() { return maxLlmCalls; } public void setMaxLlmCalls(int value) { maxLlmCalls = value; }
    public int getLlmCallsUsed() { return llmCallsUsed; } public void setLlmCallsUsed(int value) { llmCallsUsed = value; }
    public int getMaxRunSeconds() { return maxRunSeconds; } public void setMaxRunSeconds(int value) { maxRunSeconds = value; }
    public String getEvidenceJson() { return evidenceJson; } public void setEvidenceJson(String value) { evidenceJson = value; }
    public String getEvidenceHash() { return evidenceHash; } public void setEvidenceHash(String value) { evidenceHash = value; }
    public String getFindingJson() { return findingJson; } public void setFindingJson(String value) { findingJson = value; }
    public String getStopReason() { return stopReason; } public void setStopReason(String value) { stopReason = value; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getApprovedAt() { return approvedAt; } public void setApprovedAt(LocalDateTime value) { approvedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime value) { completedAt = value; }
    public List<AgentRun> getTrace() { return trace; } public void setTrace(List<AgentRun> value) { trace = value == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<AgentRun>(value)); }

    private static List<String> immutable(List<String> value) {
        return value == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<String>(value));
    }
}
