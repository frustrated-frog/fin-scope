package com.finscope.domain.quant.execution;

import lombok.Data;

/** Frozen replay contract; times use Asia/Shanghai. */
@Data
public class FrozenSignalBatch {
    private java.time.LocalDate signalDate;
    private java.time.LocalDateTime informationCutoff;
    private java.time.LocalDateTime trainingLabelsMaturedBefore;
    private String protocolVersion;
    private com.finscope.common.enums.quant.ReplaySignalMethod signalMethod =
            com.finscope.common.enums.quant.ReplaySignalMethod.TRAINED_MODEL;
    private String modelVersion;
    private String dataFingerprint;
    private String universeEvidence;
    private java.util.List<FrozenCandidate> candidates;
}
