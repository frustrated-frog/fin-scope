package com.finscope.service.factorresearch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CapitalResearchDraftCommand {
    private final String instrumentCode;
    private final String instrumentName;
    private final LocalDateTime observedAt;
    private final String signalCode;
    private final Long snapshotId;
    private final String snapshotFingerprint;
    private final List<String> evidenceRefs;
    private final List<String> objectiveTags;

    public CapitalResearchDraftCommand(String instrumentCode, String instrumentName,
                                       LocalDateTime observedAt, String signalCode,
                                       Long snapshotId, String snapshotFingerprint,
                                       List<String> evidenceRefs, List<String> objectiveTags) {
        this.instrumentCode = instrumentCode;
        this.instrumentName = instrumentName;
        this.observedAt = observedAt;
        this.signalCode = signalCode;
        this.snapshotId = snapshotId;
        this.snapshotFingerprint = snapshotFingerprint;
        this.evidenceRefs = copy(evidenceRefs);
        this.objectiveTags = copy(objectiveTags);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(values));
    }

    public String getInstrumentCode() { return instrumentCode; }
    public String getInstrumentName() { return instrumentName; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public String getSignalCode() { return signalCode; }
    public Long getSnapshotId() { return snapshotId; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public List<String> getObjectiveTags() { return objectiveTags; }
}
