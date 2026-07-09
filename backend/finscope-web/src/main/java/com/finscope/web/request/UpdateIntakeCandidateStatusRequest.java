package com.finscope.web.request;

public class UpdateIntakeCandidateStatusRequest {
    private String humanStatus;
    private String humanNote;

    public String getHumanStatus() {
        return humanStatus;
    }

    public void setHumanStatus(String humanStatus) {
        this.humanStatus = humanStatus;
    }

    public String getHumanNote() {
        return humanNote;
    }

    public void setHumanNote(String humanNote) {
        this.humanNote = humanNote;
    }
}
