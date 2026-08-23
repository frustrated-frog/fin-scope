package com.finscope.domain.marketpulse;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Python 行情服务返回的全行业历史快照。 */
@Data
public class SectorHistorySnapshot {
    private LocalDate businessDate;
    private String sourceCode;
    private String sourceFamily;
    private String qualityStatus;
    private LocalDateTime retrievedAt;
    private int requestedWindow;
    private List<LocalDate> coveredTradeDates = new ArrayList<>();
    private List<SectorHistoryItem> entries = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
