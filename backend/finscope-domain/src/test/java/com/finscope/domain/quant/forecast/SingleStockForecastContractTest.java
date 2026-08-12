package com.finscope.domain.quant.forecast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SingleStockForecastContractTest {

    @Test
    void exposesContextCompetitionLeakageAndQlibReferenceEvidence() {
        SingleStockForecast forecast = new SingleStockForecast();
        SingleStockForecast.ForecastContext context = new SingleStockForecast.ForecastContext();
        SingleStockForecast.ContextSource market = new SingleStockForecast.ContextSource();
        market.setCode("000300.SH");
        context.setMarket(market);
        forecast.setContext(context);
        SingleStockForecast.ModelCompetition competition = new SingleStockForecast.ModelCompetition();
        competition.setSelectedModel("LOGISTIC");
        SingleStockForecast.ModelCandidate candidate = new SingleStockForecast.ModelCandidate();
        candidate.setCode("LOGISTIC");
        candidate.setRole("CHAMPION");
        candidate.setCalibratedProbability(.61d);
        competition.getCandidates().add(candidate);
        forecast.setModelCompetition(competition);
        SingleStockForecast.LeakageAudit audit = new SingleStockForecast.LeakageAudit();
        audit.setStatus("PASSED");
        forecast.setLeakageAudit(audit);
        SingleStockForecast.QlibReference qlib = new SingleStockForecast.QlibReference();
        qlib.setRuntimeDependency(false);
        forecast.setQlibReference(qlib);

        assertEquals("000300.SH", forecast.getContext().getMarket().getCode());
        assertEquals("LOGISTIC", forecast.getModelCompetition().getSelectedModel());
        assertEquals("CHAMPION", forecast.getModelCompetition().getCandidates().get(0).getRole());
        assertEquals(.61d, forecast.getModelCompetition().getCandidates().get(0)
                .getCalibratedProbability(), .000001d);
        assertEquals("PASSED", forecast.getLeakageAudit().getStatus());
        assertFalse(forecast.getQlibReference().isRuntimeDependency());
    }
    @Test
    void exposesVersionThreeQualificationEvidence() {
        SingleStockForecast value = new SingleStockForecast();
        value.setRawProbability(0.68d);
        SingleStockForecast.ConfidenceInterval probabilityInterval = new SingleStockForecast.ConfidenceInterval();
        probabilityInterval.setStatus("AVAILABLE");
        probabilityInterval.setLower(0.52d);
        probabilityInterval.setUpper(0.70d);
        value.setProbabilityInterval(probabilityInterval);

        SingleStockForecast.ProbabilityMetricSet metrics = new SingleStockForecast.ProbabilityMetricSet();
        metrics.setSampleCount(15);
        SingleStockForecast.LockedTestReport locked = new SingleStockForecast.LockedTestReport();
        locked.setCalibratedMetrics(metrics);
        SingleStockForecast.SplitSliceAudit development = new SingleStockForecast.SplitSliceAudit();
        development.setPurgedCount(20);
        SingleStockForecast.QualificationSplitAudit audit = new SingleStockForecast.QualificationSplitAudit();
        audit.setDevelopment(development);
        SingleStockForecast.ModelQualification qualification = new SingleStockForecast.ModelQualification();
        qualification.setStatus("QUALIFIED");
        qualification.setLockedTest(locked);
        qualification.setSplitAudit(audit);
        value.setQualification(qualification);

        assertEquals(0.68d, value.getRawProbability(), 0.000001d);
        assertEquals(0.52d, value.getProbabilityInterval().getLower(), 0.000001d);
        assertEquals("QUALIFIED", value.getQualification().getStatus());
        assertEquals(15, value.getQualification().getLockedTest().getCalibratedMetrics().getSampleCount());
        assertEquals(20, value.getQualification().getSplitAudit().getDevelopment().getPurgedCount());
    }

    @Test
    void keepsVersionTwoFieldsOptional() {
        SingleStockForecast value = new SingleStockForecast();
        value.setReportSchemaVersion("single-stock-research-v2");

        assertNull(value.getQualification());
        assertNull(value.getRawProbability());
    }
}
