package com.finscope.service.financials;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalFinancialRefreshServiceTest {
    @Test
    void createsALocalResearchInstrumentAndRunsTheExistingFinancialPipeline() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        FinancialRefreshService refresh = mock(FinancialRefreshService.class);
        FinancialReportView expected = new FinancialReportView();
        when(instruments.findByCodeTypeAndMarket("AAPL", "STOCK", "US")).thenReturn(Optional.empty());
        when(instruments.save(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument value = invocation.getArgument(0);
            value.setId(71L);
            return value;
        });
        when(refresh.refresh(71L, LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL))
                .thenReturn(expected);

        FinancialReportView result = new GlobalFinancialRefreshService(instruments, refresh)
                .refresh("SEC_EDGAR", "CIK0000320193", "Apple Inc.", "AAPL", "Nasdaq",
                        LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        assertEquals(expected, result);
        verify(instruments).save(any(Instrument.class));
        verify(refresh).refresh(71L, LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);
    }

    @Test
    void persistsTheSecIdentityWithoutAddingTheCompanyToAWatchlist() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        FinancialRefreshService refresh = mock(FinancialRefreshService.class);
        when(instruments.findByCodeTypeAndMarket("GOOGL", "STOCK", "US")).thenReturn(Optional.empty());
        when(instruments.save(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument value = invocation.getArgument(0);
            value.setId(72L);
            assertEquals("US", value.getMarket());
            assertEquals("SEC_CIK:0001652044", value.getAliases());
            return value;
        });

        new GlobalFinancialRefreshService(instruments, refresh)
                .refresh("SEC_EDGAR", "CIK0001652044", "Alphabet Inc.", "GOOGL", "Nasdaq",
                        LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        verify(instruments).save(any(Instrument.class));
    }

    @Test
    void persistsAKoreanIdentityAndRunsTheDartPipeline() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        FinancialRefreshService refresh = mock(FinancialRefreshService.class);
        when(instruments.findByCodeTypeAndMarket("000660", "STOCK", "KR")).thenReturn(Optional.empty());
        when(instruments.save(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument value = invocation.getArgument(0);
            value.setId(73L);
            assertEquals("KR", value.getMarket());
            assertEquals("KRX_SYMBOL:000660", value.getAliases());
            return value;
        });

        new GlobalFinancialRefreshService(instruments, refresh).refresh(
                "KRX_KIND", "000660", "SK hynix Inc.", "000660", "KRX",
                LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        verify(refresh).refresh(73L, LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);
    }

    @Test
    void doesNotOverwriteAnAShareThatHasTheSameSixDigitCode() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        FinancialRefreshService refresh = mock(FinancialRefreshService.class);
        Instrument aShare = new Instrument();
        aShare.setId(11L);
        aShare.setCode("000660");
        aShare.setType("STOCK");
        aShare.setMarket("SZ");
        when(instruments.findByCodeAndType("000660", "STOCK")).thenReturn(Optional.of(aShare));
        when(instruments.findByCodeTypeAndMarket("000660", "STOCK", "KR"))
                .thenReturn(Optional.empty());
        when(instruments.save(any(Instrument.class))).thenAnswer(invocation -> {
            Instrument value = invocation.getArgument(0);
            value.setId(74L);
            return value;
        });

        new GlobalFinancialRefreshService(instruments, refresh).refresh(
                "KRX_KIND", "000660", "SK hynix Inc.", "000660", "KRX",
                LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);

        assertEquals("SZ", aShare.getMarket());
        verify(instruments, never()).update(aShare);
        verify(refresh).refresh(74L, LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);
    }
}
