package com.finscope.domain.fundamentals;

import lombok.Data;
import java.time.LocalDateTime;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;

@Data
public class FundamentalWatchReview {
    private Long id;
    private Long watchId;
    private FundamentalWatchStatus status;
    private String note;
    private LocalDateTime reviewedAt;
    private java.time.LocalDate nextReviewDate;
    private FundamentalWatchComparison comparison;
}
