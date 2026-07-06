package com.finscope.domain.source;

import java.net.URI;
import java.util.Locale;

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

    // 官方/央行/数据
    FEDERAL_RESERVE("FEDERAL_RESERVE", "美联储", 5, "官方"),
    BEA("BEA", "美国经济分析局", 5, "官方"),
    SEC("SEC", "美国证券交易委员会", 5, "官方"),
    BIS("BIS", "国际清算银行", 5, "官方"),
    BANK_OF_ENGLAND("BANK_OF_ENGLAND", "英国央行", 5, "官方"),
    IEA("IEA", "国际能源署", 5, "官方"),
    CONFERENCE_BOARD("CONFERENCE_BOARD", "世界大型企业联合会", 4, "数据"),
    GOV_CN("GOV_CN", "中国政府网", 5, "官方"),
    NBS_CN("NBS_CN", "国家统计局", 5, "官方"),
    CSRC("CSRC", "证监会", 5, "官方"),

    // 财经媒体
    TONGHUASHUN("TONGHUASHUN", "同花顺", 4, "财经"),
    EASTMONEY("EASTMONEY", "东方财富", 4, "财经"),
    SINA_FINANCE("SINA_FINANCE", "新浪财经", 4, "财经"),
    TENCENT_FINANCE("TENCENT_FINANCE", "腾讯财经", 4, "财经"),
    SECURITIES_TIMES("SECURITIES_TIMES", "证券时报", 4, "财经"),
    SECURITIES_STAR("SECURITIES_STAR", "证券之星", 4, "财经"),

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

        // 社交媒体
        if (matchesHost(url, "x.com", "twitter.com")) {
            return X;
        }

        // 新闻媒体
        if (matchesHost(url, "xinhuanet.com", "news.cn")) {
            return XINHUA;
        }
        if (matchesHost(url, "people.com.cn")) {
            return PEOPLE;
        }
        if (matchesHost(url, "caixin.com")) {
            return CAIXIN;
        }

        // 官方/央行/数据
        if (matchesHost(url, "federalreserve.gov")) {
            return FEDERAL_RESERVE;
        }
        if (matchesHost(url, "bea.gov")) {
            return BEA;
        }
        if (matchesHost(url, "sec.gov")) {
            return SEC;
        }
        if (matchesHost(url, "bis.org")) {
            return BIS;
        }
        if (matchesHost(url, "bankofengland.co.uk")) {
            return BANK_OF_ENGLAND;
        }
        if (matchesHost(url, "iea.org")) {
            return IEA;
        }
        if (matchesHost(url, "conference-board.org")) {
            return CONFERENCE_BOARD;
        }
        if (matchesHost(url, "stats.gov.cn")) {
            return NBS_CN;
        }
        if (matchesHost(url, "csrc.gov.cn")) {
            return CSRC;
        }
        if (matchesHost(url, "gov.cn")) {
            return GOV_CN;
        }

        // 财经媒体
        if (matchesHost(url, "10jqka.com.cn", "hexun.com")) {
            return TONGHUASHUN;
        }
        if (matchesHost(url, "eastmoney.com", "guba.eastmoney.com")) {
            return EASTMONEY;
        }
        if (matchesHost(url, "finance.sina.com.cn")) {
            return SINA_FINANCE;
        }
        if (matchesHost(url, "finance.qq.com")) {
            return TENCENT_FINANCE;
        }
        if (matchesHost(url, "stcn.com")) {
            return SECURITIES_TIMES;
        }
        if (matchesHost(url, "stockstar.com")) {
            return SECURITIES_STAR;
        }

        // 研究平台
        if (matchesHost(url, "arxiv.org")) {
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

    private static boolean matchesHost(String url, String... domains) {
        String host = host(url);
        if (host.isEmpty()) {
            return false;
        }
        for (String domain : domains) {
            if (domain == null || domain.trim().isEmpty()) {
                continue;
            }
            String normalized = domain.trim().toLowerCase(Locale.ROOT);
            if (host.equals(normalized) || host.endsWith("." + normalized)) {
                return true;
            }
        }
        return false;
    }

    private static String host(String url) {
        try {
            String host = new URI(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }
}
