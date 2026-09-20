package com.finscope.domain.investmentobservation;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import com.finscope.common.enums.investmentobservation.ReactionPathType;

@Data
public class ReactionCalculation {
    private ReactionProfile profile;
    private String methodVersion = "DAILY_RETURN_DIFFERENCE_V1";
    private String benchmarkCode = "000300.SH";
    private String benchmarkName = "沪深300";
    private String adjustment = "QFQ";
    private String stockSource;
    private String benchmarkSource;
    private String stockQuality;
    private String benchmarkQuality;
    private LocalDate stockAsOf;
    private LocalDate benchmarkAsOf;
    private LocalDate baselineDate;
    private LocalDate firstSession;
    private LocalDateTime calculatedAt;
    private ReactionPathType pathType = ReactionPathType.OBSERVING;
    private List<String> warnings = new ArrayList<>();
    private List<ReactionPoint> points = new ArrayList<>();
    private List<ReactionWindow> windows = new ArrayList<>();
}
