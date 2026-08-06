package com.finscope.service.cache;

import java.time.LocalDateTime;

/** 页面快照可见版本；事件不携带页面数据，客户端据此重新读取轻量快照。 */
public final class ViewRevision {
    private final String scope;
    private final long revision;
    private final LocalDateTime completedAt;

    public ViewRevision(String scope, long revision, LocalDateTime completedAt) {
        this.scope = scope;
        this.revision = revision;
        this.completedAt = completedAt;
    }

    public String getScope() { return scope; }
    public long getRevision() { return revision; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
