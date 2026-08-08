package com.finscope.domain.radar;

/**
 * 雷达信号的持久化状态。
 *
 * <p>集中表达信号是否仍在聚合窗口内，避免在 Repository/Service 中散落字符串判断。</p>
 */
public enum RadarSignalStatus {
    ACTIVE,
    EXPIRED;

    public String code() {
        return name();
    }

    /**
     * 将持久化的状态字符串解析为枚举；未知或空值返回 {@code null}。
     */
    public static RadarSignalStatus from(String value) {
        if (value == null) return null;
        for (RadarSignalStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) return status;
        }
        return null;
    }
}
