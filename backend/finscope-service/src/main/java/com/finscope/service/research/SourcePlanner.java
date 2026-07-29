package com.finscope.service.research;

import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SourcePlanner {
    @Resource
    private ThemeProfileService themeProfileService;

    public List<SourceProfile> plan(LocalDate runDate,
                                    List<String> themeCodes,
                                    List<SourceProfile> sourceProfiles) {
        themeProfileService.getRequired(themeCodes);
        List<SourceProfile> planned = new ArrayList<SourceProfile>();
        Set<Long> selectedSourceIds = new LinkedHashSet<Long>();
        for (String themeCode : themeCodes) {
            ThemeProfile theme = themeProfileService.getRequired(themeCode);
            List<SourceProfile> candidates = new ArrayList<SourceProfile>();
            for (SourceProfile profile : emptyIfNull(sourceProfiles)) {
                if (!profile.isEnabled()) {
                    continue;
                }
                String sourceTier = normalized(profile.getSourceTier());
                if (containsTier(theme.getDisallowedTiers(), sourceTier)) {
                    continue;
                }
                if (profile.getThemeCodes().contains(theme.getCode())) {
                    candidates.add(profile);
                } else if (containsTier(theme.getRequiredTiers(), sourceTier)
                        || containsTier(theme.getPreferredTiers(), sourceTier)) {
                    candidates.add(profile);
                }
            }
            Collections.sort(candidates, byTheme(theme));
            for (SourceProfile candidate : candidates) {
                if (candidate.getSourceId() != null && selectedSourceIds.contains(candidate.getSourceId())) {
                    continue;
                }
                planned.add(candidate);
                if (candidate.getSourceId() != null) {
                    selectedSourceIds.add(candidate.getSourceId());
                }
            }
        }
        return planned;
    }

    private Comparator<SourceProfile> byTheme(final ThemeProfile theme) {
        return new Comparator<SourceProfile>() {
            @Override
            public int compare(SourceProfile left, SourceProfile right) {
                int scoreCompare = Integer.compare(score(theme, right), score(theme, left));
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                String leftName = left.getSourceName() == null ? "" : left.getSourceName();
                String rightName = right.getSourceName() == null ? "" : right.getSourceName();
                int nameCompare = leftName.compareToIgnoreCase(rightName);
                if (nameCompare != 0) {
                    return nameCompare;
                }
                long leftId = left.getSourceId() == null ? Long.MAX_VALUE : left.getSourceId();
                long rightId = right.getSourceId() == null ? Long.MAX_VALUE : right.getSourceId();
                return Long.compare(leftId, rightId);
            }
        };
    }

    private int score(ThemeProfile theme, SourceProfile profile) {
        int score = profile.getCredibility() == null ? 0 : profile.getCredibility() * 100;
        String tier = normalized(profile.getSourceTier());
        if (containsTier(theme.getRequiredTiers(), tier)) {
            score += 40;
        }
        if (containsTier(theme.getPreferredTiers(), tier)) {
            score += 20;
        }
        if (profile.getThemeCodes().contains(theme.getCode())) {
            score += 1000;
        }
        return score;
    }

    private boolean containsTier(List<String> tiers, String candidate) {
        for (String tier : tiers) {
            if (normalized(tier).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<SourceProfile> emptyIfNull(List<SourceProfile> profiles) {
        return profiles == null ? Collections.<SourceProfile>emptyList() : profiles;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
