package com.finscope.domain.financials;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BrokerResearchCandidate {
    private String sourceCode;
    private String externalId;
    private String sourceUrl;
    private String stockCode;
    private String title;
    private String institution;
    private String analyst;
    private LocalDate publishedDate;
    private String rating;
    private String reportType;
    private Integer pageCount;
    private Long importedReportId;
    private String availability;
}
