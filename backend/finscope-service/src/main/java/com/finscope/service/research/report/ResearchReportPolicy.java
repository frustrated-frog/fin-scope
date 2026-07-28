package com.finscope.service.research.report;

public final class ResearchReportPolicy {
    public static final int TARGET_REPORT_MIN_CHARACTERS = 8000;
    public static final int TARGET_REPORT_MAX_CHARACTERS = 16000;
    public static final int MAX_REPORT_CHARACTERS = 24000;
    public static final int MAX_EXECUTIVE_SUMMARY_CHARACTERS = 1200;
    public static final int MAX_CLAIM_CHARACTERS = 280;

    private ResearchReportPolicy() {
    }

    public static String bound(String value, int maximum) {
        if (value == null) return "";
        String compact = value.trim();
        if (compact.length() <= maximum) return compact;
        return compact.substring(0, Math.max(0, maximum - 12)).trim() + "\n\n（已截断）";
    }
}
