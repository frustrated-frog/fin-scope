package com.finscope.domain.quant.academy;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuantStrategyAcademyBuildResult {
    private int scannedCount;
    private int draftCreatedCount;
    private int versionConfirmedCount;
    private int experimentStartedCount;
    private int reusedCount;
    private int failedCount;
    private List<BuildItem> items = new ArrayList<BuildItem>();

    @Data
    public static class BuildItem {
        private Long candidateId;
        private String title;
        private String status;
        private String message;
        private Long strategyVersionId;
        private Long experimentId;
    }
}
