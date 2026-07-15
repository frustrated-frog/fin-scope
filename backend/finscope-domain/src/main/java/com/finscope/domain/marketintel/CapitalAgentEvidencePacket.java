package com.finscope.domain.marketintel;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 传给资金行为 Agent 的不可变证据快照。
 */
@Getter
public final class CapitalAgentEvidencePacket {
    private final Long snapshotId;
    private final Long instrumentId;
    private final LocalDateTime asOf;
    private final String snapshotFingerprint;
    private final String evidenceFingerprint;
    private final String qualityStatus;
    private final String factorVersion;
    private final String signalVersion;
    private final String ruleVersion;
    private final String promptVersion;
    private final List<CapitalFactorObservation> factorObservations;
    private final List<CapitalBehaviorSignal> signals;
    private final List<CapitalEvidenceRef> rawMetrics;
    private final List<CapitalSignalEvaluation> historicalEvaluations;
    private final List<String> allowedHypotheses;
    private final List<CapitalWatchCondition> watchConditions;
    private final List<String> dataGaps;
    private final List<String> coverageDimensions;
    private final boolean sufficientCoverage;

    public CapitalAgentEvidencePacket(Long snapshotId, Long instrumentId, LocalDateTime asOf,
                                      String snapshotFingerprint, String evidenceFingerprint,
                                      String qualityStatus, String factorVersion, String signalVersion,
                                      String ruleVersion, String promptVersion,
                                      List<CapitalFactorObservation> factorObservations,
                                      List<CapitalBehaviorSignal> signals,
                                      List<CapitalEvidenceRef> rawMetrics,
                                      List<CapitalSignalEvaluation> historicalEvaluations,
                                      List<String> allowedHypotheses,
                                      List<CapitalWatchCondition> watchConditions,
                                      List<String> dataGaps, List<String> coverageDimensions,
                                      boolean sufficientCoverage) {
        this.snapshotId = snapshotId;
        this.instrumentId = instrumentId;
        this.asOf = asOf;
        this.snapshotFingerprint = snapshotFingerprint;
        this.evidenceFingerprint = evidenceFingerprint;
        this.qualityStatus = qualityStatus;
        this.factorVersion = factorVersion;
        this.signalVersion = signalVersion;
        this.ruleVersion = ruleVersion;
        this.promptVersion = promptVersion;
        this.factorObservations = immutable(factorObservations);
        this.signals = immutable(signals);
        this.rawMetrics = immutable(rawMetrics);
        this.historicalEvaluations = immutable(historicalEvaluations);
        this.allowedHypotheses = immutable(allowedHypotheses);
        this.watchConditions = immutable(watchConditions);
        this.dataGaps = immutable(dataGaps);
        this.coverageDimensions = immutable(coverageDimensions);
        this.sufficientCoverage = sufficientCoverage;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null
                ? Collections.<T>emptyList() : values));
    }
}
