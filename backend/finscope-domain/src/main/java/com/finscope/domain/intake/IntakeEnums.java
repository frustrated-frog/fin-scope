package com.finscope.domain.intake;

public final class IntakeEnums {
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";

    public static final String BATCH_RUNNING = "RUNNING";
    public static final String BATCH_COMPLETED = "COMPLETED";
    public static final String BATCH_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    public static final String BATCH_FAILED = "FAILED";

    public static final String AGENT_PROMOTABLE = "PROMOTABLE";
    public static final String AGENT_NEED_REVIEW = "NEED_REVIEW";
    public static final String AGENT_LOW_VALUE = "LOW_VALUE";
    public static final String AGENT_DUPLICATE = "DUPLICATE";
    public static final String AGENT_OFF_TOPIC = "OFF_TOPIC";
    public static final String AGENT_EXTRACTION_FAILED = "EXTRACTION_FAILED";

    public static final String AGENT_PENDING = "PENDING";
    public static final String AGENT_SUCCESS = "SUCCESS";
    public static final String AGENT_FAILED = "FAILED";
    public static final String AGENT_FALLBACK = "FALLBACK";

    public static final String HUMAN_PENDING = "PENDING";
    public static final String HUMAN_PROMOTED = "PROMOTED";
    public static final String HUMAN_SAVED_FOR_LATER = "SAVED_FOR_LATER";
    public static final String HUMAN_SKIPPED = "SKIPPED";
    public static final String HUMAN_REJECTED = "REJECTED";

    private IntakeEnums() {
    }
}
