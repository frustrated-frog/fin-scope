package com.finscope.domain.research;

import com.finscope.domain.source.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SourceProfile {
    private Long sourceId;
    private String sourceName;
    private String sourceTier;
    private List<String> themeCodes = Collections.emptyList();
    private Integer credibility;
    private boolean enabled;

    public static SourceProfile from(Source source) {
        SourceProfile profile = new SourceProfile();
        profile.setSourceId(source.getId());
        profile.setSourceName(source.getName());
        profile.setSourceTier(resolveTier(source));
        profile.setThemeCodes(resolveThemeCodes(source));
        profile.setCredibility(source.getCredibility());
        profile.setEnabled(source.isEnabled());
        return profile;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceTier() {
        return sourceTier;
    }

    public void setSourceTier(String sourceTier) {
        this.sourceTier = sourceTier;
    }

    public List<String> getThemeCodes() {
        return themeCodes;
    }

    public void setThemeCodes(List<String> themeCodes) {
        if (themeCodes == null || themeCodes.isEmpty()) {
            this.themeCodes = Collections.emptyList();
            return;
        }
        List<String> normalized = new ArrayList<String>();
        for (String themeCode : themeCodes) {
            String value = normalize(themeCode);
            if (!value.isEmpty() && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        this.themeCodes = Collections.unmodifiableList(normalized);
    }

    public Integer getCredibility() {
        return credibility;
    }

    public void setCredibility(Integer credibility) {
        this.credibility = credibility;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private static List<String> resolveThemeCodes(Source source) {
        String searchable = normalize(join(source.getName(), source.getType(), source.getUrl(), source.getTags()));
        if (searchable.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> themeCodes = new ArrayList<String>();
        for (String part : searchable.split("[,，\\s/._:\\-?=&]+")) {
            String value = normalize(part);
            if (value.isEmpty()) {
                continue;
            }
            if (value.contains("macro") || value.contains("market") || value.contains("markets")
                    || value.contains("economy") || value.contains("fed") || value.contains("财经")
                    || value.contains("金融") || value.contains("市场") || value.contains("宏观")
                    || value.contains("政策")) {
                themeCodes.add(ResearchEnums.THEME_CHINA_MACRO);
            } else if (value.contains("ai") || value.contains("artificial") || value.contains("intelligence")
                    || value.contains("arxiv") || value.contains("人工智能") || value.contains("大模型")
                    || value.contains("机器学习") || value.contains("创业")) {
                themeCodes.add(ResearchEnums.THEME_AI_STARTUP);
            } else if (value.contains("ipo") || value.contains("company") || value.contains("公司")
                    || value.contains("上市") || value.contains("融资") || value.contains("财报")
                    || value.contains("产业")) {
                themeCodes.add(ResearchEnums.THEME_COMPANY_IPO);
            } else if (value.equals(ResearchEnums.THEME_CHINA_MACRO)
                    || value.equals(ResearchEnums.THEME_AI_STARTUP)
                    || value.equals(ResearchEnums.THEME_COMPANY_IPO)
                    || value.equals(ResearchEnums.THEME_MARKET)) {
                themeCodes.add(value);
            }
        }
        if (themeCodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> deduplicated = new ArrayList<String>();
        for (String themeCode : themeCodes) {
            if (!deduplicated.contains(themeCode)) {
                deduplicated.add(themeCode);
            }
        }
        return Collections.unmodifiableList(deduplicated);
    }

    private static String resolveTier(Source source) {
        String type = normalize(source.getType());
        String searchable = normalize(join(source.getName(), source.getType(), source.getUrl(), source.getTags()));
        if (type.contains("official") || type.contains("regulator") || searchable.contains("official")
                || searchable.contains("regulator") || searchable.contains("监管") || searchable.contains("官方")) {
            return ResearchEnums.SOURCE_TIER_REGULATOR;
        }
        if (type.contains("company") || searchable.contains("company") || searchable.contains("公司")) {
            return ResearchEnums.SOURCE_TIER_COMPANY;
        }
        if (type.contains("social") || type.contains("twitter") || type.contains("x.com")) {
            return ResearchEnums.SOURCE_TIER_SOCIAL;
        }
        if (type.contains("ai") || searchable.contains("curated_ai") || searchable.contains("arxiv")
                || searchable.contains("cs.ai")) {
            return "CURATED_AI";
        }
        if (type.contains("rss") || type.contains("feed") || searchable.contains("marketwatch")
                || searchable.contains("dj.com") || searchable.contains("zhihu")) {
            return ResearchEnums.SOURCE_TIER_MEDIA;
        }
        if (source.getCredibility() >= 5) {
            return ResearchEnums.SOURCE_TIER_OFFICIAL;
        }
        if (source.getCredibility() >= 4) {
            return ResearchEnums.SOURCE_TIER_MEDIA;
        }
        return ResearchEnums.SOURCE_TIER_UNKNOWN;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(value);
            }
        }
        return builder.toString();
    }
}
