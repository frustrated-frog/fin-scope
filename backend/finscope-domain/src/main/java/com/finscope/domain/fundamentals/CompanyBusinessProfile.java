package com.finscope.domain.fundamentals;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CompanyBusinessProfile {
    private Long instrumentId;
    private String companyName;
    private String industry;
    private String description;
    private String businessScope;
    private String sourceUrl;
    private LocalDateTime fetchedAt;
}
