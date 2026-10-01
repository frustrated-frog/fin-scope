package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionResolutionStatus;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReactionStockResolution {
    private ReactionResolutionStatus status = ReactionResolutionStatus.NO_SUBJECT;
    private List<ReactionStockMatch> matches = new ArrayList<>();
    private String evidence;
}
