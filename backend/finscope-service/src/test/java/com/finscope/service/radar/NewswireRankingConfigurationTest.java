package com.finscope.service.radar;

import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.radar.NewsworthinessAssessment;
import com.finscope.domain.radar.RadarEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NewswireRankingConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(ConfigurationUnderTest.class);

    @Test
    void bindsConfiguredBonusAndInjectsTheSamePolicyIntoProductionBeans() {
        context.withPropertyValues("finscope.newswire-ranking.max-personal-bonus=7").run(application -> {
            assertNull(application.getStartupFailure());
            var event = new RadarEvent(); event.setCanonicalTitle("300750披露新进展");
            var value = new NewsworthinessAssessment(); value.setPublicScore(50); event.setNewsworthiness(value);
            var followed = new WatchlistItem(); followed.setCode("300750"); followed.setName("宁德时代");
            var result = application.getBean(RadarPriorityService.class).score(event, List.of(), List.of(followed), LocalDateTime.now(), 90, 90);
            assertEquals(57, result.getTotalScore());
            var empty = application.getBean(RadarNewsworthinessService.class).assess(null, 0, null, LocalDateTime.now());
            assertEquals(0, empty.getPublicScore());
        });
    }

    @Test
    void refusesInvalidBoundWeightsDuringStartup() {
        context.withPropertyValues("finscope.newswire-ranking.importance-weight=0.9").run(application -> {
            assertNotNull(application.getStartupFailure());
        });
    }

    @Configuration
    @EnableConfigurationProperties(NewswireRankingProperties.class)
    @Import({RadarNewsworthinessService.class, RadarPriorityService.class})
    static class ConfigurationUnderTest { }
}
