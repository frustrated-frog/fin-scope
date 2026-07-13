package com.finscope.domain.knowledge;

/**
 * Canonical persisted vocabulary for the knowledge domain.
 *
 * <p>Persistence values are intentionally parsed strictly. A value that is not
 * declared here represents schema or data drift and must not silently fall back
 * to a default state.</p>
 */
public final class KnowledgeEnums {
    private KnowledgeEnums() {
    }

    public enum LearningStatus {
        SUGGESTED, TODO, IN_PROGRESS, DONE, DISMISSED;

        public static LearningStatus parse(String value) {
            return parseStrict(LearningStatus.class, value);
        }
    }

    public enum TopicLifecycle {
        ACTIVE, PAUSED, ARCHIVED;

        public static TopicLifecycle parse(String value) {
            return parseStrict(TopicLifecycle.class, value);
        }
    }

    public enum TopicMastery {
        EXPLORING, BUILDING, REVIEWING, MATURE;

        public static TopicMastery parse(String value) {
            return parseStrict(TopicMastery.class, value);
        }
    }

    public enum EntryType {
        ANSWER, INSIGHT, CONCLUSION, REVIEW;

        public static EntryType parse(String value) {
            return parseStrict(EntryType.class, value);
        }
    }

    public enum EntryStatus {
        DRAFT, FINAL;

        public static EntryStatus parse(String value) {
            return parseStrict(EntryStatus.class, value);
        }
    }

    public enum Confidence {
        LOW, MEDIUM, HIGH;

        public static Confidence parse(String value) {
            return parseStrict(Confidence.class, value);
        }
    }

    public enum CompletionMode {
        RECORDED, LEGACY;

        public static CompletionMode parse(String value) {
            return parseStrict(CompletionMode.class, value);
        }
    }

    private static <E extends Enum<E>> E parseStrict(Class<E> enumType, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(enumType.getSimpleName() + " must not be blank");
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Unknown " + enumType.getSimpleName() + " value: " + value,
                    error
            );
        }
    }
}
