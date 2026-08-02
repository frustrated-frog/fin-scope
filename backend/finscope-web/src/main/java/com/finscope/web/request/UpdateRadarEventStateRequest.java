package com.finscope.web.request;

public class UpdateRadarEventStateRequest {
    private Boolean read;
    private Boolean followed;
    private String disposition;
    public Boolean getRead() { return read; }
    public void setRead(Boolean value) { read = value; }
    public Boolean getFollowed() { return followed; }
    public void setFollowed(Boolean value) { followed = value; }
    public String getDisposition() { return disposition; }
    public void setDisposition(String value) { disposition = value; }
}
