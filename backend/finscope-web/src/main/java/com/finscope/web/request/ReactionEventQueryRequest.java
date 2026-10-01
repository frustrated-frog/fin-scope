package com.finscope.web.request;

import com.finscope.common.enums.investmentobservation.ReactionWorkspaceView;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionResolutionStatus;
import lombok.Data;

@Data
public class ReactionEventQueryRequest {
    private ReactionWorkspaceView view = ReactionWorkspaceView.TRACKING;
    private ReactionEventType eventType;
    private ReactionResolutionStatus resolutionStatus;
    private String query = "";
    private boolean followed;
    private boolean changedToday;
    private int page = 1;
    private int size = 20;
    private long anchor = Long.MAX_VALUE;
    public com.finscope.domain.investmentobservation.ReactionEventQuery toQuery() {
        var result = new com.finscope.domain.investmentobservation.ReactionEventQuery();
        result.setView(view);
        result.setEventType(eventType);
        result.setResolutionStatus(resolutionStatus);
        result.setQuery(query);
        result.setFollowed(followed);
        result.setChangedToday(changedToday);
        result.setPage(page);
        result.setSize(size);
        result.setAnchor(anchor);
        return result;
    }
}
