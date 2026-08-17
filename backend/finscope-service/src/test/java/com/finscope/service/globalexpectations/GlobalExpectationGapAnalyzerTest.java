package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationEventGroup;
import com.finscope.domain.globalexpectations.GlobalExpectationRadarMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExpectationGapAnalyzerTest {
    private final GlobalExpectationGapAnalyzer analyzer = new GlobalExpectationGapAnalyzer();

    @Test
    void classifiesExpectationLeadingWhenPredictionMovesWithoutRecentNews() {
        GlobalExpectationEventGroup group = group(68, "READY", List.of());

        analyzer.analyze(List.of(group));

        assertEquals("EXPECTATION_LEADING", group.getExpectationRealityState());
        assertEquals(68, group.getExpectationScore());
        assertEquals(0, group.getRealityScore());
        assertFalse(group.getGapReasons().isEmpty());
    }

    @Test
    void classifiesRealityLeadingWhenNewsAcceleratesBeforePrediction() {
        GlobalExpectationEventGroup group = group(22, "READY", List.of(match(4, 1, 8, 3)));

        analyzer.analyze(List.of(group));

        assertEquals("REALITY_LEADING", group.getExpectationRealityState());
        assertEquals(100, group.getRealityScore());
        assertEquals(4, group.getNewsCount1h());
        assertEquals(8, group.getNewsCount24h());
        assertEquals(3, group.getIndependentSourceCount());
    }

    @Test
    void classifiesDualAcceleratingWhenBothSidesAreActive() {
        GlobalExpectationEventGroup group = group(72, "READY", List.of(match(4, 1, 8, 3)));

        analyzer.analyze(List.of(group));

        assertEquals("DUAL_ACCELERATING", group.getExpectationRealityState());
    }

    @Test
    void classifiesQuietWhenNeitherSideHasEnoughActivity() {
        GlobalExpectationEventGroup group = group(18, "READY", List.of());

        analyzer.analyze(List.of(group));

        assertEquals("QUIET", group.getExpectationRealityState());
    }

    @Test
    void exposesInsufficientDataInsteadOfPretendingRealityIsQuiet() {
        GlobalExpectationEventGroup group = group(74, "FAILED", List.of());

        analyzer.analyze(List.of(group));

        assertEquals("INSUFFICIENT_DATA", group.getExpectationRealityState());
        assertEquals(0, group.getRealityScore());
    }

    private GlobalExpectationEventGroup group(int signalScore, String realityDataStatus,
                                                List<GlobalExpectationRadarMatch> matches) {
        GlobalExpectationEventGroup group = new GlobalExpectationEventGroup();
        group.setSignalScore(signalScore);
        group.setRealityDataStatus(realityDataStatus);
        group.setRadarMatches(matches);
        return group;
    }

    private GlobalExpectationRadarMatch match(int newsCount1h, int newsCountPrevious1h,
                                               int newsCount24h, int independentSourceCount) {
        GlobalExpectationRadarMatch match = new GlobalExpectationRadarMatch();
        match.setNewsCount1h(newsCount1h);
        match.setNewsCountPrevious1h(newsCountPrevious1h);
        match.setNewsCount24h(newsCount24h);
        match.setIndependentSourceCount(independentSourceCount);
        return match;
    }
}
