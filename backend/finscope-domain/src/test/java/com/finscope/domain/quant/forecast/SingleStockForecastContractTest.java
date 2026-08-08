package com.finscope.domain.quant.forecast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SingleStockForecastContractTest {
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
