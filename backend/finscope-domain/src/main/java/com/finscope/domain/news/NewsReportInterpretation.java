package com.finscope.domain.news;

import com.finscope.common.enums.news.NewsInterpretationStatus;
import lombok.Data;

import java.util.List;

/** 单一报道正文版本的解读结果。 */
@Data
public class NewsReportInterpretation {
    private int contentVersion;
    private NewsInterpretationStatus status;
    private Result result;
    private String failureMessage;

    @Data
    public static class Result {
        private List<String> facts;
        private String whyItMatters;
        private List<String> unknowns;
        private List<String> nextChecks;
    }
}
