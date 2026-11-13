package com.finscope.domain.fundamentals;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;

@Data
public class FundamentalWatch {
    private Long id;
    private Long instrumentId;
    private String originKey;
    private String title;
    private String hypothesis;
    private String baseline;
    private String supportCondition;
    private String challengeCondition;
    private LocalDate nextReviewDate;
    private FundamentalWatchStatus status = FundamentalWatchStatus.TRACKING;
    private int revision;
    private LocalDateTime createdAt;
    private List<FundamentalWatchReview> reviews = new ArrayList<>();
}
