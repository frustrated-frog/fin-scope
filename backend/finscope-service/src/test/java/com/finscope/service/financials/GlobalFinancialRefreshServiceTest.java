package com.finscope.service.financials;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalFinancialRefreshServiceTest {
    @Test
    void createsALocalResearchInstrumentAndRunsTheExistingFinancialPipeline() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        FinancialRefreshService refresh = mock(FinancialRefreshService.class);
        FinancialReportView expected = new FinancialReportView();
        when(instruments.findByCodeAndType("AAPL", "STOCK")).thenReturn(Optional.empty());
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
        when(instruments.findByCodeAndType("GOOGL", "STOCK")).thenReturn(Optional.empty());
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
    void rejectsAProviderThatDoesNotYetHaveStructuredFinancialFetching() {
        GlobalFinancialRefreshService service = new GlobalFinancialRefreshService(
                mock(InstrumentRepository.class), mock(FinancialRefreshService.class));

        assertThrows(BusinessException.class, () -> service.refresh(
                "KRX_KIND", "000660", "SK hynix Inc.", "000660", "KRX",
                LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL));
    }
}
