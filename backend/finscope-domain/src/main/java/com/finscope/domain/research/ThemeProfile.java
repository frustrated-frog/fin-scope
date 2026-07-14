package com.finscope.domain.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThemeProfile {
    /**
     * 业务编码。
     */
    private String code;
    /**
     * 名称。
     */
    private String name;
    /**
     * 描述信息。
     */
    private String description;
    /**
     * 简报栏目分段。
     */
    private String briefSection;
    /**
     * 必需来源层级列表。
     */
    private List<String> requiredTiers = Collections.emptyList();
    /**
     * 优先来源层级列表。
     */
    private List<String> preferredTiers = Collections.emptyList();
    /**
     * 禁用来源层级列表。
     */
    private List<String> disallowedTiers = Collections.emptyList();
    /**
     * 是否启用创作策略。
     */
    private boolean creatorEnabled;
    /**
     * 偏好的内容形式列表。
     */
    private List<String> preferredFormats = Collections.emptyList();

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBriefSection() {
        return briefSection;
    }

    public void setBriefSection(String briefSection) {
        this.briefSection = briefSection;
    }

    public List<String> getRequiredTiers() {
        return requiredTiers;
    }

    public void setRequiredTiers(List<String> requiredTiers) {
        this.requiredTiers = immutableCopy(requiredTiers);
    }

    public List<String> getPreferredTiers() {
        return preferredTiers;
    }

    public void setPreferredTiers(List<String> preferredTiers) {
        this.preferredTiers = immutableCopy(preferredTiers);
    }

    public List<String> getDisallowedTiers() {
        return disallowedTiers;
    }

    public void setDisallowedTiers(List<String> disallowedTiers) {
        this.disallowedTiers = immutableCopy(disallowedTiers);
    }

    public boolean isCreatorEnabled() {
        return creatorEnabled;
    }

    public void setCreatorEnabled(boolean creatorEnabled) {
        this.creatorEnabled = creatorEnabled;
    }

    public List<String> getPreferredFormats() {
        return preferredFormats;
    }

    public void setPreferredFormats(List<String> preferredFormats) {
        this.preferredFormats = immutableCopy(preferredFormats);
    }

    private List<String> immutableCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
