package com.finscope.web.request.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.service.factorresearch.CapitalResearchDraftCommand;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class CapitalResearchDraftRequest {
    private String instrumentCode;
    private String instrumentName;
    private LocalDateTime observedAt;
    private String signalCode;
    private Long snapshotId;
    private String snapshotFingerprint;
    private List<String> evidenceRefs = Collections.emptyList();
    private List<String> objectiveTags = Collections.emptyList();

    public CapitalResearchDraftCommand toCommand() {
        require(instrumentCode, "标的代码不能为空");
        require(instrumentName, "标的名称不能为空");
        require(observedAt, "观察时间不能为空");
        require(signalCode, "信号代码不能为空");
        require(snapshotId, "快照标识不能为空");
        require(snapshotFingerprint, "快照指纹不能为空");
        return new CapitalResearchDraftCommand(instrumentCode, instrumentName, observedAt, signalCode,
                snapshotId, snapshotFingerprint, evidenceRefs, objectiveTags);
    }

    private static void require(Object value, String message) {
        if (value == null || value instanceof String && ((String) value).trim().isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message);
        }
    }

    public String getInstrumentCode() { return instrumentCode; }
    public void setInstrumentCode(String instrumentCode) { this.instrumentCode = instrumentCode; }
    public String getInstrumentName() { return instrumentName; }
    public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }
    public String getSignalCode() { return signalCode; }
    public void setSignalCode(String signalCode) { this.signalCode = signalCode; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> evidenceRefs) { this.evidenceRefs = evidenceRefs; }
    public List<String> getObjectiveTags() { return objectiveTags; }
    public void setObjectiveTags(List<String> objectiveTags) { this.objectiveTags = objectiveTags; }
}
