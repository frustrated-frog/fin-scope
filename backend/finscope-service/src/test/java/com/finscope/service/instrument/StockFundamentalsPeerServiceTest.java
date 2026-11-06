package com.finscope.service.instrument;

import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockFundamentals;
import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.service.valuation.StockValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockFundamentalsPeerServiceTest {
    private InstrumentRepository instruments;
    private StockValuationService valuations;
    private StockFundamentalsPeerService service;
    private StockFundamentals result;
    private StockValuationSnapshot current;

    @BeforeEach
    void setUp() {
        instruments = mock(InstrumentRepository.class);
        valuations = mock(StockValuationService.class);
        service = new StockFundamentalsPeerService();
        ReflectionTestUtils.setField(service, "instruments", instruments);
        ReflectionTestUtils.setField(service, "valuations", valuations);
        result = new StockFundamentals();
        Instrument stock = stock(17L);
        stock.setSectorCode("SECTOR_A");
        stock.setMarket("SH");
        result.setInstrument(stock);
        current = valuation("20", "2").getLatest();
        StockValuationView currentView = new StockValuationView();
        currentView.setLatest(current);
        result.setValuation(currentView);
    }

    @Test
    void comparesOnlySameDayAndSameSourceLocalPositiveSamples() {
        when(instruments.findSectorPeers("SECTOR_A", "SH", 17L, 8))
                .thenReturn(List.of(stock(21L), stock(22L), stock(23L), stock(24L)));
        StockValuationView valid = valuation("18.4", "1.8");
        StockValuationView wrongDate = valuation("19", "2");
        wrongDate.getLatest().setObservedDate(LocalDate.of(2026, 11, 5));
        StockValuationView wrongSource = valuation("21", "3");
        wrongSource.getLatest().setSourceCode("OTHER");
        when(valuations.view(21L)).thenReturn(valid);
        when(valuations.view(22L)).thenReturn(wrongDate);
        when(valuations.view(23L)).thenReturn(wrongSource);
        when(valuations.view(24L)).thenReturn(new StockValuationView());

        service.populate(result);

        assertEquals(1, result.getPeers().size());
        assertEquals("600021", result.getPeers().get(0).getCode());
        assertEquals(new BigDecimal("18.4"), result.getPeers().get(0).getPeTtm());
        assertEquals("FRESH_PRIMARY", result.getPeers().get(0).getQualityStatus());
        assertEquals(current.getObservedDate(), result.getPeers().get(0).getObservedDate());
        assertEquals(current.getSourceCode(), result.getPeers().get(0).getSourceCode());
        assertTrue(result.getPeerComparisonNote().contains("本地同行样本"));
        assertTrue(result.getPeerComparisonNote().contains("不代表全行业"));
        verify(valuations, never()).refresh(anyLong());
    }

    @Test
    void checksPeAndPbSeparatelyAndNeverComparesNegativeCurrentOrPeerValues() {
        current.setPeTtm(new BigDecimal("-5"));
        when(instruments.findSectorPeers("SECTOR_A", "SH", 17L, 8))
                .thenReturn(List.of(stock(21L), stock(22L), stock(23L)));
        when(valuations.view(21L)).thenReturn(valuation("18", "1.5"));
        when(valuations.view(22L)).thenReturn(valuation("-1", "0"));
        when(valuations.view(23L)).thenReturn(valuation(null, "-2"));
        service.populate(result);
        assertEquals(1, result.getPeers().size());
        assertNull(result.getPeers().get(0).getPeTtm());
        assertEquals(new BigDecimal("1.5"), result.getPeers().get(0).getPbMrq());
    }

    @Test
    void explainsMissingIndustryOrCurrentObservationWithoutQueryingPeers() {
        result.getInstrument().setSectorCode(" ");
        service.populate(result);
        assertTrue(result.getPeerComparisonNote().contains("缺少行业归属"));
        result.getInstrument().setSectorCode("SECTOR_A");
        result.setValuation(null);
        service.populate(result);
        assertTrue(result.getPeerComparisonNote().contains("缺少观测日或来源"));
        verifyNoInteractions(instruments, valuations);
    }

    @Test
    void unavailableLocalPeerReadsKeepOtherSamplesAndCurrentData() {
        when(instruments.findSectorPeers("SECTOR_A", "SH", 17L, 8))
                .thenReturn(List.of(stock(21L), stock(22L)));
        when(valuations.view(21L)).thenThrow(new IllegalStateException("read unavailable"));
        when(valuations.view(22L)).thenReturn(valuation("19", "2.1"));
        service.populate(result);
        assertEquals(1, result.getPeers().size());
        assertSame(current, result.getValuation().getLatest());
        assertTrue(result.getWarnings().get(0).contains("本地同行估值暂不可用"));
    }

    @Test
    void explainsEmptyAndFailedLocalSampleQueriesWithoutAveragingOrRefreshing() {
        when(instruments.findSectorPeers("SECTOR_A", "SH", 17L, 8)).thenReturn(List.of());
        service.populate(result);
        assertTrue(result.getPeers().isEmpty());
        assertTrue(result.getPeerComparisonNote().contains("暂无同日同来源"));
        when(instruments.findSectorPeers("SECTOR_A", "SH", 17L, 8))
                .thenThrow(new IllegalStateException("read unavailable"));
        service.populate(result);
        assertTrue(result.getPeerComparisonNote().contains("样本暂不可用"));
        assertFalse(result.getWarnings().isEmpty());
        verifyNoInteractions(valuations);
    }

    private static Instrument stock(Long id) {
        Instrument result = new Instrument();
        result.setId(id);
        result.setCode(String.format("600%03d", id));
        result.setName("股票" + id);
        result.setType("STOCK");
        return result;
    }

    private static StockValuationView valuation(String pe, String pb) {
        StockValuationSnapshot snapshot = new StockValuationSnapshot();
        snapshot.setObservedDate(LocalDate.of(2026, 11, 6));
        snapshot.setSourceCode("LOCAL_PROVIDER");
        snapshot.setQualityStatus("FRESH_PRIMARY");
        snapshot.setPeTtm(pe == null ? null : new BigDecimal(pe));
        snapshot.setPbMrq(pb == null ? null : new BigDecimal(pb));
        StockValuationView result = new StockValuationView();
        result.setLatest(snapshot);
        return result;
    }
}
