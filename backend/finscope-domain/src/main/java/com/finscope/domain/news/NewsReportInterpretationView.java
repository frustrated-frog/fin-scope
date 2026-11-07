package com.finscope.domain.news;

import lombok.Data;

@Data
public class NewsReportInterpretationView {
    private int contentVersion;
    private boolean available;
    private NewsReportInterpretation current;
    private NewsReportInterpretation previous;
}
