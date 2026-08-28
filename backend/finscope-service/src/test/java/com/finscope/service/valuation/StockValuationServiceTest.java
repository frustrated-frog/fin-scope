package com.finscope.service.valuation;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.valuation.ValuationRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.valuation.ExternalValuationSnapshot;
import com.finscope.rpc.valuation.PythonValuationDataClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockValuationServiceTest {
    @Test
    void preservesValuationSnapshotWhenCorporateActionsAreTemporarilyUnavailable() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        ValuationRepository repository = mock(ValuationRepository.class);
        PythonValuationDataClient client = mock(PythonValuationDataClient.class);
        StockValuationService service = new StockValuationService();
        ReflectionTestUtils.setField(service, "instruments", instruments);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "percentileCalculator", new ValuationPercentileCalculator());
        when(instruments.findById(7L)).thenReturn(Optional.of(stock()));
        when(client.fetchValuation(any())).thenReturn(external());
        when(client.fetchCorporateActions(any(), any(), any())).thenThrow(
                new ProviderContractException("TEMPORARY", "公司行为暂不可用", true));
        when(repository.findHistory(eq(7L), any(LocalDate.class))).thenReturn(List.of(stored()));
        when(repository.findCorporateActions(7L, 50)).thenReturn(List.of());

        StockValuationView result = service.refresh(7L);

        verify(repository).saveSnapshot(any(StockValuationSnapshot.class));
        assertEquals(new BigDecimal("21.3"), result.getLatest().getPeTtm());
        assertEquals("公司行为暂不可用", result.getWarnings().get(result.getWarnings().size() - 1));
    }

    private static Instrument stock() {
        Instrument value = new Instrument();
        value.setId(7L); value.setCode("600519"); value.setMarket("SH");
        value.setType("STOCK"); value.setName("贵州茅台");
        return value;
    }

    private static ExternalValuationSnapshot external() {
        ExternalValuationSnapshot value = new ExternalValuationSnapshot();
        value.setObservedAt(Instant.parse("2026-08-29T02:29:58Z"));
        value.setPeTtm(new BigDecimal("21.3")); value.setPbMrq(new BigDecimal("7.1"));
        value.setSourceCode("FUYAO"); value.setQualityStatus("FRESH_PRIMARY");
        return value;
    }

    private static StockValuationSnapshot stored() {
        StockValuationSnapshot value = new StockValuationSnapshot();
        value.setInstrumentId(7L); value.setObservedDate(LocalDate.now());
        value.setObservedAt(Instant.parse("2026-08-29T02:29:58Z"));
        value.setPeTtm(new BigDecimal("21.3")); value.setPbMrq(new BigDecimal("7.1"));
        value.setSourceCode("FUYAO"); value.setQualityStatus("FRESH_PRIMARY");
        return value;
    }
}
