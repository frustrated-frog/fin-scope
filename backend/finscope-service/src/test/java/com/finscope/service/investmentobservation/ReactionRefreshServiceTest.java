package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.investmentobservation.*;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import com.finscope.rpc.quote.PythonTradingCalendarClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReactionRefreshServiceTest {
    private final ReactionSampleRepository repository = mock(ReactionSampleRepository.class);
    private final ReactionRegistrationService registration = mock(ReactionRegistrationService.class);
    private final QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
    private final PythonTradingCalendarClient calendar = mock(PythonTradingCalendarClient.class);
    private final ReactionRefreshService service = new ReactionRefreshService();
    private final ReactionSample sample = new ReactionSample();
    private final List<LocalDate> sessions = List.of("2026-09-10", "2026-09-11", "2026-09-14", "2026-09-15",
            "2026-09-16", "2026-09-17", "2026-09-18", "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24")
            .stream().map(LocalDate::parse).toList();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "registration", registration);
        ReflectionTestUtils.setField(service, "dailyBars", bars);
        ReflectionTestUtils.setField(service, "calendar", calendar);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-09-24T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
        sample.setId(1L);
        sample.setState(ReactionSampleState.OBSERVING);
        sample.setInstrumentCode("600519.SH");
        sample.setPublishedAt(LocalDateTime.parse("2026-09-17T15:00:00"));
        when(registration.require(1L)).thenReturn(sample);
        when(calendar.eventWindow(any())).thenReturn(sessions);
        when(bars.fetch(anyString(), eq(1000))).thenReturn(batch());
        when(repository.saveCalculation(eq(1L), eq(0), any(), any())).thenReturn(true);
        when(repository.saveFailure(eq(1L), eq(0), anyString(), any())).thenReturn(true);
    }

    @Test
    void afterCloseStartsNextSessionAndPersistsProvenanceWithoutCallingLlm() {
        service.refresh(1);
        verify(calendar).eventWindow(LocalDate.parse("2026-09-18"));
        verify(bars).fetch("000300.SH", 1000);
        ArgumentCaptor<ReactionCalculation> result = ArgumentCaptor.forClass(ReactionCalculation.class);
        verify(repository).saveCalculation(eq(1L), eq(0), result.capture(), any());
        assertEquals("PROVIDER", result.getValue().getStockSource());
        assertEquals(LocalDate.parse("2026-09-24"), result.getValue().getBenchmarkAsOf());
        assertEquals(3, result.getValue().getWindows().size());
    }

    @Test
    void failedOrRegressedMarketDataDoesNotOverwriteSuccessfulCalculation() {
        when(bars.fetch("600519.SH", 1000)).thenThrow(new ProviderContractException("UNAVAILABLE", "failed", true));
        service.refresh(1);
        verify(repository).saveFailure(eq(1L), eq(0), contains("保留"), any());
        verify(repository, never()).saveCalculation(anyLong(), anyInt(), any(), any());
        reset(repository);
        reset(bars);
        when(bars.fetch("000300.SH", 1000)).thenReturn(batch());
        when(repository.saveFailure(eq(1L), eq(0), anyString(), any())).thenReturn(true);
        sample.setCalculation(new ReactionCalculator().calculate(sample.getPublishedAt(), sessions,
                batch().getBars(), batch().getBars(), LocalDateTime.parse("2026-09-24T16:00:00")));
        when(bars.fetch("600519.SH", 1000)).thenReturn(new QuantDailyBarBatch(List.of(), "P", "P", "FRESH_PRIMARY",
                LocalDate.parse("2026-09-24"), List.of()));
        service.refresh(1);
        verify(repository).saveFailure(eq(1L), eq(0), anyString(), any());
        verify(repository, never()).saveCalculation(anyLong(), anyInt(), any(), any());
    }

    @Test
    void rejectsDraftAndConcurrentRevisionChangesWithoutWritingFailure() {
        sample.setState(ReactionSampleState.DRAFT);
        assertThrows(BusinessException.class, () -> service.refresh(1));
        verifyNoInteractions(bars);
        sample.setState(ReactionSampleState.OBSERVING);
        when(repository.saveCalculation(eq(1L), eq(0), any(), any())).thenReturn(false);
        assertEquals(ErrorCode.DATA_VERSION_CONFLICT, assertThrows(BusinessException.class, () -> service.refresh(1)).getErrorCode());
        verify(repository, never()).saveFailure(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void batchesAreBoundedAndSkipOverlappingIndividualWork() {
        when(repository.findDue(any(), eq(20))).thenReturn(List.of(sample));
        when(bars.fetch("600519.SH", 1000)).thenAnswer(call -> {
            assertEquals(ErrorCode.BUSINESS_CONFLICT, assertThrows(BusinessException.class, () -> service.refresh(1)).getErrorCode());
            assertTrue(service.refreshPending().isBusy());
            return batch();
        });
        assertEquals(1, service.refreshPending().getRefreshed());
    }

    private QuantDailyBarBatch batch() {
        List<QuantDailyBar> values = new ArrayList<>();
        for (LocalDate date : sessions) {
            QuantDailyBar bar = new QuantDailyBar();
            bar.setTradeDate(date);
            bar.setAdjustedClose(BigDecimal.TEN);
            bar.setVolume(BigDecimal.TEN);
            values.add(bar);
        }
        return new QuantDailyBarBatch(values, "PROVIDER", "FAMILY", "FRESH_PRIMARY", sessions.get(10), List.of());
    }
}
