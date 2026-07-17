package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialEvidence;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class FinancialEvidencePacket {
    private Long reportId;
    private String promptVersion;
    private String algorithmVersion;
    private String sourceHash;
    private String inputHash;
    private String qualityCeiling;
    private String payloadJson;
    private List<FinancialEvidence> evidence = new ArrayList<FinancialEvidence>();
    private Map<String, FinancialEvidence> evidenceIndex =
            new LinkedHashMap<String, FinancialEvidence>();
    private Set<String> allowedNumbers = new LinkedHashSet<String>();
}
