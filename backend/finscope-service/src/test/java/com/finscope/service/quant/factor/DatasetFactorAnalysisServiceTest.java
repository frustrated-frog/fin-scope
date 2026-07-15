package com.finscope.service.quant.factor;

import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.dao.factorresearch.QuantCapitalFlowRepository;
import com.finscope.domain.quant.data.QuantCapitalFlowDaily;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.service.factorresearch.CapitalFlowFactorProvider;
import com.finscope.service.factorresearch.FactorProvider;
import com.finscope.service.factorresearch.FactorProviderRegistry;
import com.finscope.service.factorresearch.LegacyQuantFactorProvider;
import com.finscope.service.quant.data.QuantDatasetService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
            double close = 100 + day * instrument; QuantDailyBar bar = new QuantDailyBar();
            bar.setInstrumentCode(String.format("60000%d.SH", instrument));
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

    @Test
    void analyzesCapitalFactorFromFrozenRowsWithoutReadingLiveMarketFlow() {
        QuantDatasetService datasets = mock(QuantDatasetService.class);
        QuantMarketDataRepository market = mock(QuantMarketDataRepository.class);
        QuantCapitalFlowRepository capital = mock(QuantCapitalFlowRepository.class);
        QuantDataset dataset = new QuantDataset();
        dataset.setId(1L); dataset.setStatus("READY"); dataset.setFingerprint("dataset-with-capital");
        when(datasets.get(1L)).thenReturn(dataset);
        when(datasets.availableFactorCodes(1L)).thenReturn(Collections.singleton("MAIN_FLOW_SHARE"));

        LocalDate date = LocalDate.of(2024, 1, 2);
        List<QuantDailyBar> bars = Arrays.asList(
                bar("600001.SH", date, "10", "10"), bar("600002.SH", date, "20", "20"),
                bar("600001.SH", date.plusDays(1), "10", "11"),
                bar("600002.SH", date.plusDays(1), "20", "19"));
        when(market.findBars(1L)).thenReturn(bars);
        when(market.findFundamentals(1L)).thenReturn(Collections.emptyList());
        when(market.findUniverseMembers(1L)).thenReturn(Collections.emptyList());
        when(capital.findByDatasetId(1L)).thenReturn(Arrays.asList(
                capital(1L, "600001.SH", date, "200", "1000"),
                capital(1L, "600002.SH", date, "100", "1000")));

        DatasetFactorAnalysisService service = new DatasetFactorAnalysisService();
        ReflectionTestUtils.setField(service, "datasets", datasets);
        ReflectionTestUtils.setField(service, "marketData", market);
        ReflectionTestUtils.setField(service, "registry", new FactorRegistry());
        ReflectionTestUtils.setField(service, "capitalFlows", capital);
        ReflectionTestUtils.setField(service, "providers", new FactorProviderRegistry(Arrays.<FactorProvider>asList(
                new LegacyQuantFactorProvider(), new CapitalFlowFactorProvider())));

        assertEquals(1, service.analyze(1L, "MAIN_FLOW_SHARE").getSampleCount());
    }

    private QuantDailyBar bar(String code, LocalDate date, String open, String close) {
        QuantDailyBar value = new QuantDailyBar();
        value.setInstrumentCode(code); value.setTradeDate(date);
        value.setOpen(new BigDecimal(open)); value.setClose(new BigDecimal(close));
        value.setAdjustedClose(new BigDecimal(close)); value.setVolume(BigDecimal.TEN);
        value.setAmount(new BigDecimal("1000"));
        return value;
    }

    private QuantCapitalFlowDaily capital(Long datasetId, String code, LocalDate date,
                                          String mainNetInflow, String amount) {
        QuantCapitalFlowDaily value = new QuantCapitalFlowDaily();
        value.setDatasetId(datasetId); value.setInstrumentCode(code); value.setTradeDate(date);
        value.setAvailableAt(LocalDateTime.of(date, java.time.LocalTime.of(18, 0)));
        value.setMainNetInflow(new BigDecimal(mainNetInflow)); value.setAmount(new BigDecimal(amount));
        value.setQualityStatus("COMPLETE"); value.setSourceFingerprint("source-" + code);
        return value;
    }
}
