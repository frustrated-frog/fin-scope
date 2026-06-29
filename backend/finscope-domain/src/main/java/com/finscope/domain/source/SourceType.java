package com.finscope.domain.source;

/**
 * 已知来源类型枚举
 */
public enum SourceType {
    // 社交媒体
    X("X", "X (Twitter)", 3, "社交"),
    TWITTER("TWITTER", "Twitter", 3, "社交"),

    // 新闻媒体
    XINHUA("XINHUA", "新华网", 5, "新闻"),
    PEOPLE("PEOPLE", "人民网", 5, "新闻"),
    CAIXIN("CAIXIN", "财新网", 5, "新闻"),

    // 财经媒体
    TONGHUASHUN("TONGHUASHUN", "同花顺", 4, "财经"),
    EASTMONEY("EASTMONEY", "东方财富", 4, "财经"),
    SINA_FINANCE("SINA_FINANCE", "新浪财经", 4, "财经"),
    TENCENT_FINANCE("TENCENT_FINANCE", "腾讯财经", 4, "财经"),

    // 研究平台
    ARXIV("ARXIV", "arXiv", 4, "研究"),

    // 通用
    WEB("WEB", "网页", 3, "通用"),
    MANUAL("MANUAL", "手动研究", 3, "通用");

    private final String code;
    private final String displayName;
    private final int credibility; // 1-5 可信度
    private final String category;

    SourceType(String code, String displayName, int credibility, String category) {
        this.code = code;
        this.displayName = displayName;
        this.credibility = credibility;
        this.category = category;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCredibility() {
        return credibility;
    }

    public String getCategory() {
        return category;
    }

    /**
     * 根据 URL 自动识别来源类型
     */
    public static SourceType fromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return WEB;
        }

        String lower = url.toLowerCase();

        // 社交媒体
        if (lower.contains("x.com") || lower.contains("twitter.com")) {
            return X;
        }

        // 新闻媒体
        if (lower.contains("xinhuanet.com") || lower.contains("news.cn")) {
            return XINHUA;
        }
        if (lower.contains("people.com.cn")) {
            return PEOPLE;
        }
        if (lower.contains("caixin.com")) {
            return CAIXIN;
        }

        // 财经媒体
        if (lower.contains("10jqka.com.cn") || lower.contains("hexun.com")) {
            return TONGHUASHUN;
        }
        if (lower.contains("eastmoney.com") || lower.contains("guba.eastmoney.com")) {
            return EASTMONEY;
        }
        if (lower.contains("finance.sina.com.cn")) {
            return SINA_FINANCE;
        }
        if (lower.contains("finance.qq.com")) {
            return TENCENT_FINANCE;
        }

        // 研究平台
        if (lower.contains("arxiv.org")) {
            return ARXIV;
        }

        return WEB;
    }

    /**
     * 根据代码获取来源类型
     */
    public static SourceType fromCode(String code) {
        if (code == null) {
            return WEB;
        }
        for (SourceType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return WEB;
    }
}
