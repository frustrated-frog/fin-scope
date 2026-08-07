package com.finscope.domain.company;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanySearchResult {
    private Long localInstrumentId;
    private String providerCode;
    private String providerCompanyId;
    private String legalName;
    private String displayName;
    private String nativeName;
    private String countryCode;
    private String industry;
    private String capabilityLevel;
    private List<CompanySecurity> securities = new ArrayList<CompanySecurity>();
}
