package com.finscope.domain.financials;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BrokerResearchForecast {
    private Long id;
    private Long researchReportId;
    private String metricCode;
    private String metricLabel;
    private LocalDate forecastPeriod;
    private BigDecimal forecastValue;
    private String unit;
    private String sourceQuote;
    private Integer sourcePage;
    private BigDecimal actualValue;
    private String actualUnit;
    private LocalDate actualPeriod;
    private BigDecimal variancePercent;
    private String verificationStatus;
    private String verificationReason;
}
