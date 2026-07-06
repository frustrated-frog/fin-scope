package com.finscope.service.article;

import com.finscope.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class ArticleCategoryPolicy {
    public static final String CATEGORY_FINANCE = "金融";
    public static final String CATEGORY_MARKET = "市场";
    public static final String CATEGORY_SELF_IMPROVEMENT = "自我提升";
    public static final String CATEGORY_FRONTIER_TECH = "前沿技术";

    private static final Set<String> SUPPORTED_CATEGORIES = new HashSet<String>(Arrays.asList(
            CATEGORY_FINANCE,
            CATEGORY_MARKET,
            CATEGORY_SELF_IMPROVEMENT,
            CATEGORY_FRONTIER_TECH));

    public String normalize(String category) {
        String normalized = StringUtils.firstNonBlank(category, CATEGORY_MARKET).trim();
        if (!SUPPORTED_CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported article category: " + category);
        }
        return normalized;
    }

    public boolean isEvidenceEligible(String category) {
        String normalized = fromLegacyCategory(category);
        return CATEGORY_FINANCE.equals(normalized) || CATEGORY_MARKET.equals(normalized);
    }

    public String fromLegacyCategory(String category) {
        if (StringUtils.isBlank(category)) {
            return CATEGORY_MARKET;
        }
        String trimmed = category.trim();
        if (SUPPORTED_CATEGORIES.contains(trimmed)) {
            return trimmed;
        }
        if (trimmed.contains("科技")) {
            return CATEGORY_FRONTIER_TECH;
        }
        if (trimmed.contains("宏观") || trimmed.contains("政策")
                || trimmed.contains("公司") || trimmed.contains("行业")) {
            return CATEGORY_FINANCE;
        }
        return CATEGORY_MARKET;
    }
}
