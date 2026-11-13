package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalBusinessResearchStatus;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class FundamentalBusinessResearch {
    private FundamentalBusinessResearchStatus status = FundamentalBusinessResearchStatus.IDLE;
    private Long latestRunId;
    private String message = "从公开资料整理收入、现金、成本和竞争优势，每项保留参考来源。";
    private boolean showingPrevious;
    private List<FundamentalBusinessFinding> findings = new ArrayList<>();
}
