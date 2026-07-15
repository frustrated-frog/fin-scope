package com.finscope.domain.marketintel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于一份不可变资金快照生成的、可复现的事件研究评价。
 */
@Data
public class CapitalBehaviorEvaluation {
    public static final String VERSION = "capital-evaluation-v1";

    private Long id;
    private Long instrumentId;
    private Long snapshotId;
    private LocalDateTime asOf;
    private LocalDate dataFrom;
    private LocalDate dataTo;
    private String evaluationVersion;
    private String factorVersion;
    private String signalVersion;
    private String inputFingerprint;
    private String status;
    private int dailySampleCount;
    private int evaluableEventCount;
    private BigDecimal coverageRate;
    private BigDecimal missingLossRate;
    private List<CapitalSignalEvaluation> signals = Collections.emptyList();
    private List<String> dataGaps = Collections.emptyList();
    private LocalDateTime createdAt;

    public static CapitalBehaviorEvaluation of(Long instrumentId, Long snapshotId, LocalDateTime asOf,
                                                LocalDate dataFrom, LocalDate dataTo,
                                                String factorVersion, String signalVersion,
                                                String inputFingerprint, String status,
                                                int dailySampleCount, int evaluableEventCount,
                                                BigDecimal coverageRate, BigDecimal missingLossRate,
                                                List<CapitalSignalEvaluation> signals,
                                                List<String> dataGaps) {
        CapitalBehaviorEvaluation value = new CapitalBehaviorEvaluation();
        value.instrumentId = instrumentId;
        value.snapshotId = snapshotId;
        value.asOf = asOf;
        value.dataFrom = dataFrom;
        value.dataTo = dataTo;
        value.evaluationVersion = VERSION;
        value.factorVersion = factorVersion;
        value.signalVersion = signalVersion;
        value.inputFingerprint = inputFingerprint;
        value.status = status;
        value.dailySampleCount = dailySampleCount;
        value.evaluableEventCount = evaluableEventCount;
        value.coverageRate = coverageRate;
        value.missingLossRate = missingLossRate;
        value.signals = immutable(signals);
        value.dataGaps = immutable(dataGaps);
        value.createdAt = LocalDateTime.now();
        return value;
    }

    public void setSignals(List<CapitalSignalEvaluation> values) {
        signals = immutable(values);
    }

    public void setDataGaps(List<String> values) {
        dataGaps = immutable(values);
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null
                ? Collections.<T>emptyList() : values));
    }
}
