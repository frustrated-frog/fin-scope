package com.finscope.domain.news;

import com.finscope.domain.investmentobservation.ReactionSample;
import lombok.Data;
import java.util.List;

@Data
public class NewsReportDetail {
    private NewsReport report;
    private List<NewsReportVersion> versions;
    private List<ReactionSample> reactions;
}
