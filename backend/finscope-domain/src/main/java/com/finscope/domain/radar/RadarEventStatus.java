package com.finscope.domain.radar;

/**
 * 雷达事件的持久化状态。
 *
 * <p>集中表达事件在雷达中的生命周期，避免在 Repository/Service 中散落字符串判断。</p>
 *
 * <ul>
 *   <li>{@link #ACTIVE} 正在参与雷达展示；</li>
 *   <li>{@link #QUIET} 仍在聚合窗口内但本轮没有新信号，保留待后续；</li>
 *   <li>{@link #EXPIRED} 已离开聚合窗口，不再参与雷达展示。</li>
 * </ul>
 */
public enum RadarEventStatus {
    ACTIVE,
    QUIET,
    EXPIRED;

    public String code() {
        return name();
    }

    /**
     * 将持久化的状态字符串解析为枚举；未知或空值返回 {@code null}，
     * 由调用方决定默认行为，避免掩盖未知状态。
     */
    public static RadarEventStatus from(String value) {
        if (value == null) return null;
        for (RadarEventStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) return status;
        }
        return null;
    }
}
