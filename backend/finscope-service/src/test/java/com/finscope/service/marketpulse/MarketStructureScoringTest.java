package com.finscope.service.marketpulse;

import com.finscope.common.enums.marketpulse.*;
import com.finscope.domain.marketpulse.*;
import com.finscope.domain.marketpulse.ai.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.finscope.common.enums.marketpulse.MarketStructureDimension.*;

class MarketStructureScoringTest {
    private final MarketStructureScoring scoring = new MarketStructureScoring();
    private final LocalDate date = LocalDate.of(2026, 11, 17);

    @Test
    void sameParticipationHasDifferentRepairScoreWhenPathAndDurationDiffer() {
        var today = workspace(0, .4);
        var rising = scoring.calculate(today, List.of(workspace(1, .2), workspace(2, .1)));
        var falling = scoring.calculate(today, List.of(workspace(1, .7), workspace(2, .8)));
        assertTrue(row(rising, REPAIR_QUALITY).getBaselineScore() > row(falling, REPAIR_QUALITY).getBaselineScore());
        assertEquals(row(rising, EXPANSION).getBaselineScore(), row(falling, EXPANSION).getBaselineScore());
    }

    @Test
    void oneDayBounceFromLowParticipationDoesNotImplyStrongTurningEvidence() {
        var current = workspace(0, .24);
        current.getBreadth().setMedianChangePct(-1.3);
        var rows = scoring.calculate(current, List.of(workspace(1, .2), workspace(2, .5)));
        assertTrue(row(rows, TURN_CONFIRMATION).getBaselineScore() < 50);
    }

    @Test
    void narrowTrendSampleAndMissingIndustryReturnsDoNotBecomeWholeMarketScores() {
        var today = workspace(0, .4);
        var trend = new MarketTrendBreadth();
        trend.setMa20ValidCount(66);
        trend.setMa20Ratio(.95);
        today.getBreadth().setTrendBreadth(trend);
        var sector = new SectorRotationItem();
        sector.setSectorCode("missing");
        today.setSectors(Collections.nCopies(30, sector));
        var rows = scoring.calculate(today, List.of());
        assertNull(row(rows, EXPANSION).getBaselineScore());
        assertNull(row(rows, LEADERSHIP).getBaselineScore());
        var input = new MarketAiInput();
        scoring.observations(input, today);
        assertFalse(input.getObservations().containsKey(MarketScenarioMetric.MA20_RATIO));
    }

    @Test
    void unavailableOrMismatchedDateStopsScoringAndFutureHistoryCannotAffectResult() {
        var today = workspace(0, .4);
        var baseline = scoring.calculate(today, List.of(workspace(1, .3)));
        assertEquals(baseline, scoring.calculate(today, List.of(workspace(-1, .99), workspace(1, .3))));
        today.getBreadth().setBusinessDate(date.minusDays(1));
        assertTrue(scoring.calculate(today, List.of()).stream().allMatch(r -> r.getBaselineScore() == null));
        today.getBreadth().setBusinessDate(date);
        today.getBreadth().setQualityStatus("STALE");
        assertTrue(scoring.calculate(today, List.of()).stream().allMatch(r -> r.getBaselineScore() == null));
    }

    @Test
    void changingUniverseBlocksCrossDayScoreAndEnoughHistoryAddsRelativePosition() {
        var today = workspace(0, .4);
        var previous = workspace(1, .3);
        previous.getBreadth().setValidCount(400);
        var rows = scoring.calculate(today, List.of(previous));
        assertNull(row(rows, TURN_CONFIRMATION).getBaselineScore());
        var history = new ArrayList<MarketPulseWorkspace>();
        for (int i = 1; i <= 6; i++) {
            history.add(workspace(i, .4));
        }
        var repair = row(scoring.calculate(today, history), REPAIR_QUALITY);
        assertEquals(50, repair.getComponents().get("近期可比参与度分位（6日）"));
    }

    @Test
    void adjustmentsAreAppliedOnceClampedAndDoNotMutateRules() {
        var rows = scoring.calculate(workspace(0, .9), List.of());
        var adjustment = new MarketScoreAdjustment();
        adjustment.setDimension(REPAIR_QUALITY);
        adjustment.setDelta(15);
        int original = row(rows, REPAIR_QUALITY).getBaselineScore();
        var merged = scoring.apply(rows, List.of(adjustment));
        assertEquals(Math.min(100, original + 15), row(merged, REPAIR_QUALITY).getFinalScore());
        assertEquals(original, row(rows, REPAIR_QUALITY).getFinalScore());
        assertNull(row(rows, REPAIR_QUALITY).getAdjustment());
    }

    @Test
    void nextSnapshotChecksFrozenConditionsAndRejectsIncompatibleSamples() {
        var input = new MarketAiInput();
        scoring.observations(input, workspace(0, .6));
        var previousInput = new MarketAiInput();
        scoring.observations(previousInput, workspace(1, .4));
        var previous = new MarketAiView();
        previous.setBusinessDate(date.minusDays(1));
        previous.setObservations(previousInput.getObservations());
        previous.setObservationSamples(previousInput.getObservationSamples());
        var scenario = new MarketAiScenario();
        var thesis = new MarketAiPoint();
        thesis.setTitle("修复延续");
        scenario.setThesis(thesis);
        scenario.setConfirmation(condition(MarketScenarioDirection.RISE));
        scenario.setInvalidation(condition(MarketScenarioDirection.FALL));
        var report = new MarketAiReport();
        report.setScenarios(List.of(scenario));
        previous.setReport(report);
        var review = scoring.review(previous, input).getFirst();
        assertEquals(MarketConditionStatus.MET, review.getConfirmation());
        assertEquals(MarketConditionStatus.NOT_MET, review.getInvalidation());
        assertTrue(review.getConfirmationDetail().contains("40.00% → 60.00%"));
        input.getObservationSamples().put(MarketScenarioMetric.ADVANCE_RATIO, 200);
        assertEquals(MarketConditionStatus.UNAVAILABLE, scoring.review(previous, input).getFirst().getConfirmation());
        input.getObservations().clear();
        assertEquals(MarketConditionStatus.UNAVAILABLE, scoring.review(previous, input).getFirst().getConfirmation());
    }

    private MarketScenarioCondition condition(MarketScenarioDirection direction) {
        var condition = new MarketScenarioCondition();
        condition.setDirection(direction);
        condition.setMetric(MarketScenarioMetric.ADVANCE_RATIO);
        return condition;
    }
    private MarketStructureScore row(List<MarketStructureScore> rows, MarketStructureDimension dimension) {
        return rows.stream().filter(r -> r.getDimension() == dimension).findFirst().orElseThrow();
    }
    private MarketPulseWorkspace workspace(int daysAgo, double participation) {
        var workspace = new MarketPulseWorkspace();
        workspace.setBusinessDate(date.minusDays(daysAgo));
        var breadth = new MarketBreadthSnapshot();
        breadth.setBusinessDate(workspace.getBusinessDate());
        breadth.setValidCount(5000);
        breadth.setAdvanceRatio(participation);
        breadth.setMedianChangePct(.1);
        var pressure = new MarketVolumePressure();
        pressure.setAdvanceAmountRatio(participation);
        breadth.setVolumePressure(pressure);
        workspace.setBreadth(breadth);
        return workspace;
    }
}
