package com.finscope.service.research;

import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourcePlannerTest {
    private final ThemeProfileService themeProfileService = new ThemeProfileService();
    private final SourcePlanner sourcePlanner = sourcePlanner();

    @Test
    void plansEnabledSourcesByThemeAndRespectsPerThemeLimit() {
        List<SourceProfile> planned = sourcePlanner.plan(
                LocalDate.of(2026, 6, 27),
                Arrays.asList("china_macro", "ai_startup"),
                1,
                false,
                Arrays.asList(
                        profile(1L, "Reuters Macro", "MEDIA", true, 4, "china_macro,market"),
                        profile(2L, "Fed Official", "REGULATOR", true, 5, "china_macro"),
                        profile(3L, "AI Weekly", "CURATED_AI", true, 4, "ai_startup"),
                        profile(4L, "Disabled AI", "COMPANY", false, 5, "ai_startup"))
        );

        assertEquals(2, planned.size());
        assertEquals(Arrays.asList(2L, 3L), Arrays.asList(planned.get(0).getSourceId(), planned.get(1).getSourceId()));
    }

    @Test
    void plansDisabledSourcesOnlyWhenExplicitlyRequested() {
        List<SourceProfile> planned = sourcePlanner.plan(
                LocalDate.of(2026, 6, 27),
                Collections.singletonList("ai_startup"),
                5,
                true,
                Arrays.asList(
                        profile(1L, "AI Weekly", "CURATED_AI", true, 4, "ai_startup"),
                        profile(2L, "Disabled AI", "COMPANY", false, 5, "ai_startup"))
        );

        assertEquals(Arrays.asList(2L, 1L), Arrays.asList(planned.get(0).getSourceId(), planned.get(1).getSourceId()));
    }

    private SourceProfile profile(Long id,
                                  String name,
                                  String sourceTier,
                                  boolean enabled,
                                  int credibility,
                                  String themeCodes) {
        SourceProfile profile = new SourceProfile();
        profile.setSourceId(id);
        profile.setSourceName(name);
        profile.setSourceTier(sourceTier);
        profile.setEnabled(enabled);
        profile.setCredibility(credibility);
        profile.setThemeCodes(Arrays.asList(themeCodes.split(",")));
        return profile;
    }

    private SourcePlanner sourcePlanner() {
        SourcePlanner planner = new SourcePlanner();
        ReflectionTestUtils.setField(planner, "themeProfileService", themeProfileService);
        return planner;
    }
}
