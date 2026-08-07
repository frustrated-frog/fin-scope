package com.finscope.service.financials;

import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialQueryServiceTest {
    @Test
    void keepsPreviouslyFetchedGlobalStocksInTheFinancialArchiveAfterReload() {
        InstrumentRepository instruments = mock(InstrumentRepository.class);
        when(instruments.findAll()).thenReturn(Arrays.asList(
                instrument(28L, "AAPL", "Apple Inc.", "STOCK", "US"),
                instrument(29L, "GOOGL", "Alphabet Inc.", "STOCK", "US"),
                instrument(30L, "000660", "SK hynix Inc.", "STOCK", "KR"),
                instrument(31L, "510300", "沪深300ETF", "FUND", "SH")
        ));
        FinancialQueryService service = new FinancialQueryService(
                instruments, mock(FinancialReportRepository.class));

        List<Instrument> result = service.listInstruments();

        assertEquals(3, result.size());
        assertEquals("AAPL", result.get(0).getCode());
        assertEquals("GOOGL", result.get(1).getCode());
        assertEquals("000660", result.get(2).getCode());
    }

    private static Instrument instrument(Long id, String code, String name, String type, String market) {
        Instrument value = new Instrument();
        value.setId(id);
        value.setCode(code);
        value.setName(name);
        value.setType(type);
        value.setMarket(market);
        return value;
    }
}
