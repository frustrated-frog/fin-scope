package com.finscope.domain.fundamentals;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CompanyBusinessNotes {
    private Long instrumentId;
    private String revenueModel = "";
    private String customers = "";
    private String costDrivers = "";
    private String advantage = "";
    private String sourceUrl = "";
    private int revision;
    private LocalDateTime updatedAt;
}
