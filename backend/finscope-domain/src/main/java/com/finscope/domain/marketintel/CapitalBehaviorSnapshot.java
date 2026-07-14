package com.finscope.domain.marketintel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CapitalBehaviorSnapshot {
    /**
     * 主键 ID。
     */
    private Long id;
    /**
     * 标的 ID。
     */
    private Long instrumentId;
    /**
     * 数据对应时间。
     */
    private LocalDateTime asOf;
    /**
     * 事实记录。
     */
    private List<CapitalFlowPoint> facts = Collections.emptyList();
    /**
     * 信号列表。
     */
    private List<CapitalBehaviorSignal> signals = Collections.emptyList();
    /**
     * 内容指纹。
     */
    private String fingerprint;
    /**
     * 数据质量状态。
     */
    private String qualityStatus;
    /**
     * 警告列表。
     */
    private List<String> warnings = Collections.emptyList();
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    public static CapitalBehaviorSnapshot of(Long instrumentId, LocalDateTime asOf,
                                             List<CapitalFlowPoint> facts,
                                             List<CapitalBehaviorSignal> signals,
                                             String fingerprint) {
        CapitalBehaviorSnapshot snapshot = new CapitalBehaviorSnapshot();
        snapshot.instrumentId = instrumentId;
        snapshot.asOf = asOf;
        snapshot.facts = immutable(facts);
        snapshot.signals = immutable(signals);
        snapshot.fingerprint = fingerprint;
        snapshot.qualityStatus = "COMPLETE";
        snapshot.createdAt = LocalDateTime.now();
        return snapshot;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null ? Collections.<T>emptyList() : values));
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public LocalDateTime getAsOf() { return asOf; }
    public void setAsOf(LocalDateTime asOf) { this.asOf = asOf; }
    public List<CapitalFlowPoint> getFacts() { return facts; }
    public void setFacts(List<CapitalFlowPoint> facts) { this.facts = immutable(facts); }
    public List<CapitalBehaviorSignal> getSignals() { return signals; }
    public void setSignals(List<CapitalBehaviorSignal> signals) { this.signals = immutable(signals); }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getQualityStatus() { return qualityStatus; }
    public void setQualityStatus(String qualityStatus) { this.qualityStatus = qualityStatus; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = immutable(warnings); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
