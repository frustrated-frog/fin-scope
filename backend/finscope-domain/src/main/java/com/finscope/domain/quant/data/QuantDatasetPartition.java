package com.finscope.domain.quant.data;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable manifest entry for one logical partition of a quant dataset.
 */
@Data
public class QuantDatasetPartition {
    private Long datasetId;
    private String partitionType;
    private long rowCount;
    private LocalDate minDate;
    private LocalDate maxDate;
    private String partitionFingerprint;
    private String qualityStatus;
    private LocalDateTime createdAt;
}
