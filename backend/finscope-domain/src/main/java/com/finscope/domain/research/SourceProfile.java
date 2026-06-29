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
        profile.setThemeCodes(parseThemeCodes(source.getTags()));
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

    private static List<String> parseThemeCodes(String rawTags) {
        if (rawTags == null || rawTags.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> themeCodes = new ArrayList<String>();
        for (String part : rawTags.split("[,，\\s]+")) {
            String value = normalize(part);
            if (value.isEmpty()) {
                continue;
            }
            if (value.contains("macro")) {
                themeCodes.add(ResearchEnums.THEME_CHINA_MACRO);
            } else if (value.contains("ai")) {
                themeCodes.add(ResearchEnums.THEME_AI_STARTUP);
            } else if (value.contains("ipo") || value.contains("company")) {
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
        String tags = normalize(source.getTags());
        if (type.contains("official") || type.contains("regulator") || tags.contains("official") || tags.contains("regulator")) {
            return ResearchEnums.SOURCE_TIER_REGULATOR;
        }
        if (type.contains("company") || tags.contains("company")) {
            return ResearchEnums.SOURCE_TIER_COMPANY;
        }
        if (type.contains("social") || type.contains("twitter") || type.contains("x")) {
            return ResearchEnums.SOURCE_TIER_SOCIAL;
        }
        if (type.contains("ai") || tags.contains("curated_ai")) {
            return "CURATED_AI";
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
}
