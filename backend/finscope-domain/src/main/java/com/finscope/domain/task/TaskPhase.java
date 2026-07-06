package com.finscope.domain.task;

public enum TaskPhase {
    QUEUED,
    FETCHING,
    PARSING,
    PERSISTING,
    LLM,
    COMPLETED,
    FAILED
}
