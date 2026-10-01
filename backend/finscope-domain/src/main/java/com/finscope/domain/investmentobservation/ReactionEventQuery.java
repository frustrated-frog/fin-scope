package com.finscope.domain.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionWorkspaceView;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionResolutionStatus;
import lombok.Data;

@Data
public class ReactionEventQuery {
    private ReactionWorkspaceView view = ReactionWorkspaceView.TRACKING;
    private ReactionEventType eventType;
    private ReactionResolutionStatus resolutionStatus;
    private String query = "";
    private boolean followed;
    private boolean changedToday;
    private int page = 1;
    private int size = 20;
    private long anchor = Long.MAX_VALUE;
}
