package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.NextSessionPredictionRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.forecast.NextSessionPrediction;
import com.finscope.domain.quant.forecast.NextSessionPredictionRecord;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NextSessionPredictionServiceTest {
    @Test
    void settlesExactNextCloseUsingSameVintagePricesAfterCloseOnly() {
        var repository = mock(NextSessionPredictionRepository.class);
        var source = mock(QuantDailyBarSource.class);
        var service = service(repository, source);
        var record = record();
        when(repository.findPending(100)).thenReturn(List.of(record));
        var batch = new QuantDailyBarBatch(List.of(bar("2026-09-04", 9), bar("2026-09-07", 9.18)),
                "TEST", "TEST", "FRESH_PRIMARY", LocalDate.of(2026, 9, 7), List.of());
        when(source.fetch("000001.SZ", 5000)).thenReturn(batch);

        service.settle(LocalDateTime.of(2026, 9, 7, 14, 0));
        verifyNoInteractions(source);
        service.settle(LocalDateTime.of(2026, 9, 7, 16, 0));

        verify(repository).settle(eq(1L), doubleThat(value -> Math.abs(value - 0.02) < 1e-9),
                eq(true), eq(true), any(), eq("TEST"));
    }

    @Test
    void neverSubstitutesAnotherDateForMissingTargetClose() {
        var repository = mock(NextSessionPredictionRepository.class);
        var source = mock(QuantDailyBarSource.class);
        var service = service(repository, source);
        when(repository.findPending(100)).thenReturn(List.of(record()));
        var batch = new QuantDailyBarBatch(List.of(bar("2026-09-04", 10), bar("2026-09-08", 11)),
                "TEST", "TEST", "FRESH_PRIMARY", LocalDate.of(2026, 9, 8), List.of());
        when(source.fetch("000001.SZ", 5000)).thenReturn(batch);

        service.settle(LocalDateTime.of(2026, 9, 9, 16, 0));

        verify(repository, never()).settle(any(), anyDouble(), anyBoolean(), anyBoolean(), any(), any());
        verify(repository).unavailable(eq(1L), any(), contains("目标交易日"));
    }

    private NextSessionPredictionService service(NextSessionPredictionRepository repository, QuantDailyBarSource source) {
        var value = new NextSessionPredictionService();
        ReflectionTestUtils.setField(value, "repository", repository);
        ReflectionTestUtils.setField(value, "dailyBars", source);
        return value;
    }

    private NextSessionPredictionRecord record() {
        var prediction = new NextSessionPrediction();
        prediction.setAsOfDate(LocalDate.of(2026, 9, 4));
        prediction.setTargetDate(LocalDate.of(2026, 9, 7));
        prediction.setLastClose(10d);
        prediction.setUpProbability(0.65);
        prediction.setLowerReturn(-0.03);
        prediction.setUpperReturn(0.04);
        var record = new NextSessionPredictionRecord();
        record.setId(1L);
        record.setInstrumentCode("000001.SZ");
        record.setPrediction(prediction);
        return record;
    }

    private QuantDailyBar bar(String date, double close) {
        var value = new QuantDailyBar();
        value.setTradeDate(LocalDate.parse(date));
        value.setClose(BigDecimal.valueOf(close));
        return value;
    }
}
