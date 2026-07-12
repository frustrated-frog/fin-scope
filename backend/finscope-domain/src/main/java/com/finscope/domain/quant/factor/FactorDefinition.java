package com.finscope.domain.quant.factor;

public class FactorDefinition {
    private final String code;
    private final String name;
    private final String category;
    private final String direction;
    private final String description;
    private final int lookbackDays;
    private final boolean pointInTime;

    public FactorDefinition(String code, String name, String category, String direction,
                            String description, int lookbackDays, boolean pointInTime) {
        this.code = code; this.name = name; this.category = category; this.direction = direction;
        this.description = description; this.lookbackDays = lookbackDays; this.pointInTime = pointInTime;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDirection() { return direction; }
    public String getDescription() { return description; }
    public int getLookbackDays() { return lookbackDays; }
    public boolean isPointInTime() { return pointInTime; }
}
