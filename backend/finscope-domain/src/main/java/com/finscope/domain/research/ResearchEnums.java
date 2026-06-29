package com.finscope.domain.research;

public final class ResearchEnums {
    public static final String THEME_AI_STARTUP = "ai_startup";
    public static final String THEME_CHINA_MACRO = "china_macro";
    public static final String THEME_COMPANY_IPO = "company_ipo";
    public static final String THEME_MARKET = "market";

    public static final String EVENT_ACTIVE = "ACTIVE";
    public static final String EVENT_COOLING = "COOLING";
    public static final String EVENT_ARCHIVED = "ARCHIVED";

    public static final String NOVELTY_NEW = "NEW";
    public static final String NOVELTY_FOLLOW_UP = "FOLLOW_UP";
    public static final String NOVELTY_RECAP = "RECAP";
    public static final String NOVELTY_DUPLICATE = "DUPLICATE";
    public static final String NOVELTY_NOISE = "NOISE";

    public static final String RELATION_PRIMARY = "PRIMARY";
    public static final String RELATION_SUPPORTING = "SUPPORTING";

    public static final String SOURCE_TIER_OFFICIAL = "OFFICIAL";
    public static final String SOURCE_TIER_REGULATOR = "REGULATOR";
    public static final String SOURCE_TIER_COMPANY = "COMPANY";
    public static final String SOURCE_TIER_MEDIA = "MEDIA";
    public static final String SOURCE_TIER_SOCIAL = "SOCIAL";
    public static final String SOURCE_TIER_UNKNOWN = "UNKNOWN";

    public static final String EVIDENCE_FACT = "FACT";
    public static final String EVIDENCE_DATA = "DATA";
    public static final String EVIDENCE_TIMELINE = "TIMELINE";

    public static final String LEARNING_STATUS_TODO = "TODO";
    public static final String LEARNING_STATUS_LEARNING = "LEARNING";
    public static final String LEARNING_STATUS_REVIEWING = "REVIEWING";
    public static final String LEARNING_STATUS_DONE = "DONE";

    public static final String LEARNING_DIFFICULTY_FOUNDATION = "FOUNDATION";
    public static final String LEARNING_DIFFICULTY_INTERMEDIATE = "INTERMEDIATE";
    public static final String LEARNING_DIFFICULTY_ADVANCED = "ADVANCED";

    public static final String CONTENT_FORMAT_LONG_ARTICLE = "LONG_ARTICLE";
    public static final String CONTENT_FORMAT_SHORT_VIDEO = "SHORT_VIDEO";
    public static final String CONTENT_FORMAT_PODCAST = "PODCAST";
    public static final String CONTENT_FORMAT_X_THREAD = "X_THREAD";
    public static final String CONTENT_FORMAT_XIAOHONGSHU_NOTE = "XIAOHONGSHU_NOTE";

    public static final String CONTENT_STATUS_IDEA = "IDEA";
    public static final String CONTENT_STATUS_DRAFTING = "DRAFTING";
    public static final String CONTENT_STATUS_READY = "READY";
    public static final String CONTENT_STATUS_PUBLISHED = "PUBLISHED";
    public static final String CONTENT_STATUS_ARCHIVED = "ARCHIVED";

    public static final String RUN_STATUS_PLANNED = "PLANNED";
    public static final String RUN_STATUS_RUNNING = "RUNNING";
    public static final String RUN_STATUS_COMPLETED = "COMPLETED";
    public static final String RUN_STATUS_FAILED = "FAILED";

    private ResearchEnums() {
    }
}
