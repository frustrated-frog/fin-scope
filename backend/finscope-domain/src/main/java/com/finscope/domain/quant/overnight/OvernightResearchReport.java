package com.finscope.domain.quant.overnight;

import com.finscope.common.enums.overnight.OvernightMode;
import com.finscope.common.enums.overnight.OvernightStatus;
import com.finscope.common.enums.overnight.OvernightEvidenceKind;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class OvernightResearchReport {
    private String id;
    private String modelVersion;
    private OvernightMode mode;
    private OvernightStatus status;
    private String instrumentCode;
    private String signalDate;
    private String cutoff;
    private String generatedAt;
    private String dataThrough;
    private String inputFingerprint;
    private String targetDate;
    private Double referencePrice;
    private double costBps;
    private Double costBasis;
    private Double quantity;
    private OvernightEvidenceKind evidenceKind;
    private String entryRule;
    private String executionStatus;
    private String sourceCode;
    private List<Map<String, Object>> targets;
    private List<String> warnings;
    private Map<String, Object> request;
    private Map<String, Object> outcome;
}
