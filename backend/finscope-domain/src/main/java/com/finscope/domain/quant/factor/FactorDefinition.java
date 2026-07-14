package com.finscope.domain.quant.factor;

public class FactorDefinition {
    /**
     * 因子编码。
     */
    private final String code;
    /**
     * 因子名称。
     */
    private final String name;
    /**
     * 因子分类。
     */
    private final String category;
    /**
     * 方向。
     */
    private final String direction;
    /**
     * 因子描述。
     */
    private final String description;
    /**
     * 回看天数。
     */
    private final int lookbackDays;
    /**
     * 是否满足时间点数据要求。
     */
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
