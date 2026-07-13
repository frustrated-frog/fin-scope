package com.finscope.service.knowledge;

public final class KnowledgeProjectionRequested {
    private final long jobId;
    private final long topicId;
    private final long entryId;

    public KnowledgeProjectionRequested(long jobId, long topicId, long entryId) {
        this.jobId = jobId;
        this.topicId = topicId;
        this.entryId = entryId;
    }

    public long getJobId() {
        return jobId;
    }

    public long getTopicId() {
        return topicId;
    }

    public long getEntryId() {
        return entryId;
    }
}
