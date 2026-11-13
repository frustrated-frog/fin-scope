package com.finscope.web.response;

import com.finscope.common.enums.fundamentals.FundamentalBusinessResearchStatus;
import com.finscope.domain.fundamentals.FundamentalBusinessFinding;
import com.finscope.domain.fundamentals.FundamentalBusinessResearch;
import lombok.Data;
import java.util.List;

@Data
public class FundamentalBusinessResearchResponse {
    private FundamentalBusinessResearchStatus status;
    private Long latestRunId;
    private String message;
    private boolean showingPrevious;
    private List<FundamentalBusinessFinding> findings;

    public static FundamentalBusinessResearchResponse of(FundamentalBusinessResearch value) {
        var response = new FundamentalBusinessResearchResponse();
        response.setStatus(value.getStatus());
        response.setLatestRunId(value.getLatestRunId());
        response.setMessage(value.getMessage());
        response.setShowingPrevious(value.isShowingPrevious());
        response.setFindings(value.getFindings());
        return response;
    }
}
