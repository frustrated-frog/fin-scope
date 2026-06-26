package com.finscope.common.util;

/**
 * 字符串工具类
 */
public final class StringUtils {

    /**
     * 返回第一个非空字符串
     */
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * 安全返回字符串，如果为空则返回默认值
     */
    public static String safe(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    /**
     * 截断字符串到指定长度
     */
    public static String truncate(String value, int maxLength) {
        if (isBlank(value)) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 私有构造函数防止实例化
     */
    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
