package com.finscope.domain.financials;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FinancialEvidence {
    private String id;
    private String type;
    private String label;
    private String value;
    private String unit;
    private String period;
    private String detail;
    private List<String> sourceRefs = new ArrayList<String>();
}
