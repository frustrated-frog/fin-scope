package com.finscope.service.marketpulse;

import com.finscope.domain.marketpulse.MarketBreadthSnapshot;
import com.finscope.domain.marketpulse.MarketIndexPerformance;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.marketpulse.MarketBreadthSource;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketBreadthServiceTest {

    @Test
    void enrichesBreadthWithFivePointInTimeIndexPerformances() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        MarketBreadthSource breadthSource = mock(MarketBreadthSource.class);
        QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
        when(breadthSource.fetch(date)).thenReturn(breadth(date, 0.63D));
        when(bars.fetch(anyString(), anyInt())).thenReturn(batch(date));
        MarketBreadthService service = new MarketBreadthService();
        ReflectionTestUtils.setField(service, "breadthSource", breadthSource);
        ReflectionTestUtils.setField(service, "dailyBarSource", bars);

        MarketBreadthSnapshot result = service.calculate(date);

        assertEquals(5, result.getIndices().size());
        for (MarketIndexPerformance index : result.getIndices()) {
            assertEquals(date, index.getBusinessDate());
            assertTrue(index.getReturn1d() > 0D);
            assertTrue(index.getReturn5d() > 0D);
            assertTrue(index.getReturn20d() > 0D);
        }
        assertTrue(result.getInterpretation().contains("共振"));
    }

    @Test
    void rejectsIndexBarsFromAnotherBusinessDateWithoutMixingSnapshots() {
        LocalDate date = LocalDate.of(2026, 8, 21);
        MarketBreadthSource breadthSource = mock(MarketBreadthSource.class);
        QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
        when(breadthSource.fetch(date)).thenReturn(breadth(date, 0.35D));
        when(bars.fetch(anyString(), anyInt())).thenReturn(batch(date.minusDays(1)));
        MarketBreadthService service = new MarketBreadthService();
        ReflectionTestUtils.setField(service, "breadthSource", breadthSource);
        ReflectionTestUtils.setField(service, "dailyBarSource", bars);

        MarketBreadthSnapshot result = service.calculate(date);

        assertEquals(0, result.getIndices().size());
        assertTrue(result.getWarnings().stream().anyMatch(value -> value.contains("业务日期不一致")));
    }

    private MarketBreadthSnapshot breadth(LocalDate date, double ratio) {
        MarketBreadthSnapshot value = new MarketBreadthSnapshot();
        value.setBusinessDate(date);
        value.setSourceCode("TEST");
        value.setSourceFamily("TEST");
        value.setQualityStatus("FRESH_PRIMARY");
        value.setAdvanceCount((int) Math.round(5000 * ratio));
        value.setDeclineCount(5000 - value.getAdvanceCount());
        value.setFlatCount(0);
        value.setValidCount(5000);
        value.setAdvanceRatio(ratio);
        value.setTotalAmount(2_300_000_000_000D);
        value.setLimitUpCount(68);
        value.setLimitDownCount(4);
        value.setMedianChangePct(0.7D);
        return value;
    }

    private QuantDailyBarBatch batch(LocalDate date) {
        List<QuantDailyBar> values = new ArrayList<>();
        for (int index = 24; index >= 0; index--) {
            QuantDailyBar bar = new QuantDailyBar();
            bar.setTradeDate(date.minusDays(index));
            bar.setClose(BigDecimal.valueOf(100D + 24 - index));
            bar.setOpen(bar.getClose());
            bar.setHigh(bar.getClose());
            bar.setLow(bar.getClose());
            bar.setVolume(BigDecimal.TEN);
            bar.setAmount(BigDecimal.valueOf(1_000_000));
            values.add(bar);
        }
        return new QuantDailyBarBatch(values, "TEST", "TEST", "FRESH_PRIMARY", date,
                Collections.emptyList());
    }
}
