package com.finscope.service.quant.factor;

import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.service.quant.data.QuantDatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetFactorAnalysisServiceTest {
    @Test
    void calculatesDatasetBackedRankIcAgainstNextOpenToCloseReturn() {
        QuantDatasetService datasets = mock(QuantDatasetService.class); QuantMarketDataRepository market = mock(QuantMarketDataRepository.class);
        QuantDataset dataset = new QuantDataset(); dataset.setId(1L); dataset.setStatus("READY"); dataset.setFingerprint("dataset-sha");
        when(datasets.get(1L)).thenReturn(dataset); when(datasets.availableFactorCodes(1L)).thenReturn(Collections.singleton("MOMENTUM_20D")); List<QuantDailyBar> bars = new ArrayList<QuantDailyBar>();
        for (int day = 0; day < 22; day++) for (int instrument = 1; instrument <= 3; instrument++) {
            double close = 100 + day * instrument; QuantDailyBar bar = new QuantDailyBar(); bar.setInstrumentCode("S" + instrument);
            bar.setTradeDate(LocalDate.of(2024,1,1).plusDays(day)); bar.setAdjustedClose(BigDecimal.valueOf(close));
            bar.setOpen(BigDecimal.valueOf(close * (day == 21 ? 0.99 : 1))); bar.setClose(BigDecimal.valueOf(close));
            bar.setVolume(BigDecimal.valueOf(1000)); bar.setAmount(BigDecimal.valueOf(10000)); bars.add(bar);
        }
        when(market.findBars(1L)).thenReturn(bars); when(market.findFundamentals(1L)).thenReturn(Collections.emptyList());
        when(market.findUniverseMembers(1L)).thenReturn(Collections.emptyList()); DatasetFactorAnalysisService service = new DatasetFactorAnalysisService();
        ReflectionTestUtils.setField(service,"datasets",datasets); ReflectionTestUtils.setField(service,"marketData",market);
        ReflectionTestUtils.setField(service,"registry",new FactorRegistry());
        assertEquals(1, service.analyze(1L,"MOMENTUM_20D").getSampleCount());
    }
}
