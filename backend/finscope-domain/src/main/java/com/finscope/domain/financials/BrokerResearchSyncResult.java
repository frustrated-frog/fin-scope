package com.finscope.domain.financials;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BrokerResearchSyncResult {
    private String status;
    private String sourceCode;
    private List<BrokerResearchCandidate> candidates = new ArrayList<BrokerResearchCandidate>();
    private List<BrokerResearchReport> importedReports = new ArrayList<BrokerResearchReport>();
    private int importedCount;
    private int skippedCount;
    private int failedCount;
    private List<String> errors = new ArrayList<String>();
    private LocalDateTime completedAt;
}
