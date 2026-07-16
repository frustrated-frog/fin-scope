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

    public void validate() {
        if (rowCount < 0) {
            throw new IllegalArgumentException("partition rowCount must not be negative");
        }
        if ((minDate == null) != (maxDate == null)) {
            throw new IllegalArgumentException("partition minDate and maxDate must both be set or both be null");
        }
        if (minDate != null && minDate.isAfter(maxDate)) {
            throw new IllegalArgumentException("partition minDate must not be after maxDate");
        }
    }
}
