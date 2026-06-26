package com.finscope.common.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static String text(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    public static String text(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isEmpty() ? null : LocalDateTime.parse(value);
    }

    public static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }
}
