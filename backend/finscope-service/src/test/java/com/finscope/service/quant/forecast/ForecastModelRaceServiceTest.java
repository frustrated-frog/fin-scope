package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.ForecastCandidateRunRepository;
import com.finscope.domain.quant.forecast.ForecastCandidateRun;
import com.finscope.domain.quant.forecast.ForecastModelRace;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForecastModelRaceServiceTest {
    @Test
    void accumulatesEvidenceBeforeTwelvePairedOutcomes() {
        ForecastModelRace race = evaluate(evidence(8, .65d, .80d, "UP"));

        assertEquals("EVIDENCE_ACCUMULATING", race.getStatus());
        assertEquals(8, race.getSampleCount());
        assertEquals(12, race.getMinimumPromotionSamples());
        assertNull(race.getPromotionCandidateCode());
    }

    @Test
    void marksMateriallyBetterChallengerForManualPromotionReview() {
        ForecastModelRace race = evaluate(evidence(12, .65d, .80d, "UP"));

        assertEquals("PROMOTION_REVIEW", race.getStatus());
        assertEquals("REGIME_LOGISTIC", race.getPromotionCandidateCode());
        ForecastModelRace.CandidateMetric challenger = metric(race, "REGIME_LOGISTIC");
        assertTrue(challenger.isPromotionEligible());
        assertTrue(challenger.getBrierDeltaVsChampion() < -.01d);
        assertTrue(challenger.getLogLossDeltaVsChampion() <= 0d);
    }

    @Test
    void keepsChampionWhenItStillHasTheBestTrueForwardProbabilityQuality() {
        ForecastModelRace race = evaluate(evidence(12, .80d, .60d, "UP"));

        assertEquals("CHAMPION_LEADS", race.getStatus());
        assertNull(race.getPromotionCandidateCode());
        assertTrue(metric(race, "LOGISTIC").getBrierScore()
                < metric(race, "REGIME_LOGISTIC").getBrierScore());
    }

    @Test
    void reportsNoStableEdgeWhenDifferencesDoNotClearPromotionMargin() {
        ForecastModelRace race = evaluate(evidence(12, .65d, .66d, "UP"));

        assertEquals("NO_STABLE_EDGE", race.getStatus());
        assertNull(race.getPromotionCandidateCode());
    }

    private ForecastModelRace evaluate(List<ForecastCandidateRun> evidence) {
        ForecastCandidateRunRepository repository = mock(ForecastCandidateRunRepository.class);
        when(repository.findMaturedEvidence("600519.SH", 5, 20)).thenReturn(evidence);
        return new ForecastModelRaceService(repository).evaluate("600519.SH", 5);
    }

    private ForecastModelRace.CandidateMetric metric(ForecastModelRace race, String code) {
        return race.getCandidates().stream().filter(item -> code.equals(item.getModelCode()))
                .findFirst().orElseThrow(AssertionError::new);
    }

    private List<ForecastCandidateRun> evidence(int count, double championProbability,
                                                double challengerProbability, String direction) {
        List<ForecastCandidateRun> values = new ArrayList<ForecastCandidateRun>();
        for (int index = 0; index < count; index++) {
            values.add(candidate(index, "LOGISTIC", "CHAMPION", championProbability, direction));
            values.add(candidate(index, "REGIME_LOGISTIC", "CHALLENGER",
                    challengerProbability, direction));
        }
        return values;
    }

    private ForecastCandidateRun candidate(int index, String code, String role,
                                           double probability, String direction) {
        ForecastCandidateRun value = new ForecastCandidateRun();
        value.setForecastRunId((long) index + 1L);
        value.setInstrumentCode("600519.SH");
        value.setAsOfDate(LocalDate.of(2026, 1, 1).plusDays(index));
        value.setHorizonDays(5);
        value.setModelCode(code);
        value.setModelName(code);
        value.setRole(role);
        value.setCalibratedProbability(probability);
        value.setShadowDecision(probability >= .60d ? "UP" : probability <= .40d ? "DOWN" : "ABSTAIN");
        value.setActualDirection(direction);
        value.setPredictionCorrect(value.getShadowDecision().equals(direction));
        value.setMaturityStatus("MATURED");
        return value;
    }
}
