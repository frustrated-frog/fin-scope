package com.finscope.domain.news;

public final class NewsCategory {
    private final String code;
    private final String name;
    private final String classificationGuidance;
    private final boolean enabled;
    private final int displayOrder;

    public NewsCategory(String code, String name, String classificationGuidance,
                        boolean enabled, int displayOrder) {
        this.code = code;
        this.name = name;
        this.classificationGuidance = classificationGuidance;
        this.enabled = enabled;
        this.displayOrder = displayOrder;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getClassificationGuidance() { return classificationGuidance; }
    public boolean isEnabled() { return enabled; }
    public int getDisplayOrder() { return displayOrder; }
}
