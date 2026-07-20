package com.finscope.domain.financials;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BrokerResearchClaim {
    private Long id;
    private Long researchReportId;
    private String category;
    private String title;
    private String detail;
    private String claimType;
    private String sourceQuote;
    private Integer sourcePage;
    private String financialMetricCode;
    private String financialConceptCode;
    private String verificationStatus;
    private String verificationReason;
    private String evidenceLabel;
    private String evidenceValue;
    private String evidenceUnit;
    private LocalDate evidencePeriod;
}
