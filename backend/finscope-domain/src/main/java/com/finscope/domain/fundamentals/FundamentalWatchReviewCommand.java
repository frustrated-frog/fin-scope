package com.finscope.domain.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import lombok.Data;
import java.time.LocalDate;

@Data
public class FundamentalWatchReviewCommand {
    private int revision;
    private FundamentalWatchStatus status;
    private String note;
    private LocalDate nextReviewDate;
    private String comparisonToken;
}
