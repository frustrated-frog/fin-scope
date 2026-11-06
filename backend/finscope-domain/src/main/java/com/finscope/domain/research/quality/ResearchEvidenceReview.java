package com.finscope.domain.research.quality;

import com.finscope.common.enums.research.ResearchEvidenceIssue;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchEvidenceReview {
    private String evidenceRef;
    private String url;
    private String sourceGroup;
    private String lineageReason;
    private boolean accepted;
    private List<ResearchEvidenceIssue> issues = new ArrayList<>();
}
