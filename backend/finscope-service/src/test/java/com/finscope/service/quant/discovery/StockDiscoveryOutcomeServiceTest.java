package com.finscope.service.quant.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.dao.quant.StockDiscoveryRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.discovery.StockDiscoveryAccuracyReport;
import com.finscope.domain.quant.discovery.StockDiscoveryCandidate;
import com.finscope.domain.quant.discovery.StockDiscoveryEvaluationRequest;
import com.finscope.domain.quant.discovery.StockDiscoveryModelPrediction;
import com.finscope.rpc.quant.PythonStockDiscoveryEvaluationClient;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockDiscoveryOutcomeServiceTest {
    @Test
    void settlesAtNextOpenToHorizonPlusOneOpenWithCosts() {
        StockDiscoveryRepository repository = mock(StockDiscoveryRepository.class);
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        PythonStockDiscoveryEvaluationClient client = mock(PythonStockDiscoveryEvaluationClient.class);
        StockDiscoveryCandidate candidate = candidate();
        when(repository.findPendingCandidates(200)).thenReturn(List.of(candidate));
        when(source.fetch("600001.SH", 120)).thenReturn(new QuantDailyBarBatch(
                List.of(
                        bar("2026-08-14", 10d), bar("2026-08-17", 10d), bar("2026-08-18", 10.1d),
                        bar("2026-08-19", 10.2d), bar("2026-08-20", 10.3d), bar("2026-08-21", 10.4d),
                        bar("2026-08-24", 10.5d)),
                "PYTHON_QFQ_DAILY", "AKSHARE", "FRESH_PRIMARY", LocalDate.of(2026, 8, 24), List.of()));
        when(repository.settleCandidate(eq(candidate), any(), eq(10d), any(), eq(10.5d),
                eq(0.0485d), eq("UP"), eq(true), any(), eq("PYTHON_QFQ_DAILY"))).thenReturn(true);
        StockDiscoveryOutcomeService service = service(repository, source, client);

        int settled = service.settlePending();

        assertEquals(1, settled);
        verify(repository).settleCandidate(eq(candidate), eq(LocalDate.of(2026, 8, 17)), eq(10d),
                eq(LocalDate.of(2026, 8, 24)), eq(10.5d), eq(0.0485d), eq("UP"), eq(true),
                any(), eq("PYTHON_QFQ_DAILY"));
    }

    @Test
    void buildsEvaluationOnlyFromMaturedFrozenObservations() {
        StockDiscoveryRepository repository = mock(StockDiscoveryRepository.class);
        QuantDailyBarSource source = mock(QuantDailyBarSource.class);
        PythonStockDiscoveryEvaluationClient client = mock(PythonStockDiscoveryEvaluationClient.class);
        StockDiscoveryCandidate candidate = candidate();
        candidate.setMaturityStatus("MATURED");
        candidate.setActualNetReturn(0.02d);
        candidate.setActualDirection("UP");
        candidate.setSectorNamesJson("[\"机器人\"]");
        StockDiscoveryModelPrediction model = new StockDiscoveryModelPrediction();
        model.setRunId(7L);
        model.setInstrumentCode("600001.SH");
        model.setAsOfDate(LocalDate.of(2026, 8, 14));
        model.setHorizonDays(5);
        model.setModelCode("LOGISTIC");
        model.setModelName("逻辑回归");
        model.setRole("CHAMPION");
        model.setCalibratedProbability(0.63d);
        model.setShadowDecision("UP");
        model.setQualificationStatus("QUALIFIED");
        model.setActualDirection("UP");
        when(repository.findMaturedCandidates(5000)).thenReturn(List.of(candidate));
        when(repository.findMaturedModelPredictions(20000)).thenReturn(List.of(model));
        when(repository.countPendingCandidates()).thenReturn(4);
        StockDiscoveryAccuracyReport expected = new StockDiscoveryAccuracyReport();
        when(client.evaluate(any())).thenReturn(expected);
        StockDiscoveryOutcomeService service = service(repository, source, client);

        assertEquals(expected, service.accuracy());

        ArgumentCaptor<StockDiscoveryEvaluationRequest> captor =
                ArgumentCaptor.forClass(StockDiscoveryEvaluationRequest.class);
        verify(client).evaluate(captor.capture());
        assertEquals(1, captor.getValue().getObservations().size());
        assertEquals(List.of("机器人"), captor.getValue().getObservations().get(0).getSectorNames());
        assertEquals(1, captor.getValue().getModelObservations().size());
        assertEquals(4, captor.getValue().getPendingCount());
    }

    private StockDiscoveryOutcomeService service(StockDiscoveryRepository repository,
                                                 QuantDailyBarSource source,
                                                 PythonStockDiscoveryEvaluationClient client) {
        StockDiscoveryOutcomeService service = new StockDiscoveryOutcomeService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "dailyBarSource", source);
        ReflectionTestUtils.setField(service, "evaluationClient", client);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        return service;
    }

    private StockDiscoveryCandidate candidate() {
        StockDiscoveryCandidate value = new StockDiscoveryCandidate();
        value.setId(11L);
        value.setRunId(7L);
        value.setInstrumentCode("600001.SH");
        value.setAsOfDate(LocalDate.of(2026, 8, 14));
        value.setHorizonDays(5);
        value.setAdmitted(true);
        value.setFinalRank(1);
        value.setCalibratedProbability(0.63d);
        value.setMaturityStatus("PENDING");
        value.setSectorNamesJson("[]");
        return value;
    }

    private QuantDailyBar bar(String date, double open) {
        QuantDailyBar value = new QuantDailyBar();
        value.setTradeDate(LocalDate.parse(date));
        value.setOpen(BigDecimal.valueOf(open));
        return value;
    }
}
