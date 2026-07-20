package com.finscope.domain.quant.data;

import java.time.LocalDateTime;

/** Auditable summary of one dataset-level market-data synchronization. */
public final class QuantDataSyncRun {
    private final Long id;
    private final Long datasetId;
    private final String triggerType;
    private final String status;
    private final int requestedInstruments;
    private final int succeededInstruments;
    private final int failedInstruments;
    private final int insertedRows;
    private final int degradedInstruments;
    private final String sourceSummary;
    private final String warningSummary;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;

    public QuantDataSyncRun(Long id, Long datasetId, String triggerType, String status,
                            int requestedInstruments, int succeededInstruments,
                            int failedInstruments, int insertedRows, int degradedInstruments,
                            String sourceSummary, String warningSummary,
                            LocalDateTime startedAt, LocalDateTime finishedAt) {
        this.id = id;
        this.datasetId = datasetId;
        this.triggerType = triggerType;
        this.status = status;
        this.requestedInstruments = requestedInstruments;
        this.succeededInstruments = succeededInstruments;
        this.failedInstruments = failedInstruments;
        this.insertedRows = insertedRows;
        this.degradedInstruments = degradedInstruments;
        this.sourceSummary = sourceSummary;
        this.warningSummary = warningSummary;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Long getId() { return id; }
    public Long getDatasetId() { return datasetId; }
    public String getTriggerType() { return triggerType; }
    public String getStatus() { return status; }
    public int getRequestedInstruments() { return requestedInstruments; }
    public int getSucceededInstruments() { return succeededInstruments; }
    public int getFailedInstruments() { return failedInstruments; }
    public int getInsertedRows() { return insertedRows; }
    public int getDegradedInstruments() { return degradedInstruments; }
    public String getSourceSummary() { return sourceSummary; }
    public String getWarningSummary() { return warningSummary; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
