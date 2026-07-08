package com.finscope.web.request;

public class MoveEventArticleRequest {
    private Long targetEventId;
    private Boolean createNewEvent;

    public Long getTargetEventId() {
        return targetEventId;
    }

    public void setTargetEventId(Long targetEventId) {
        this.targetEventId = targetEventId;
    }

    public Boolean getCreateNewEvent() {
        return createNewEvent;
    }

    public void setCreateNewEvent(Boolean createNewEvent) {
        this.createNewEvent = createNewEvent;
    }
}
