package com.finscope.domain.quant.data;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class QuantDataset {
    private Long id;
    private String name;
    private String market;
    private String universeType;
    private String sourceType;
    private String dataKind;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String fingerprint;
    private String qualitySummary;
    private long revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
