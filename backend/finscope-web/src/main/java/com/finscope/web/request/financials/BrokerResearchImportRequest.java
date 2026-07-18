package com.finscope.web.request.financials;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BrokerResearchImportRequest {
    @NotBlank
    private String sourceCode;
    @NotBlank
    private String externalId;
    private Long financialReportId;
}
