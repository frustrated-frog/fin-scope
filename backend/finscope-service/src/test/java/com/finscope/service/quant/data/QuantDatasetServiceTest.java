package com.finscope.service.quant.data;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import com.finscope.domain.quant.data.QuantUniverseMember;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuantDatasetServiceTest {
    @Test
    void fingerprintIsStableAcrossInputOrder() {
        QuantDatasetFingerprint fingerprint = new QuantDatasetFingerprint();
        QuantDailyBar first = bar("600000.SH", "10.00", "10.20");
        QuantDailyBar second = bar("000001.SZ", "12.00", "12.30");

        assertEquals(fingerprint.bars(Arrays.asList(first, second)),
                fingerprint.bars(Arrays.asList(second, first)));
    }

    @Test
    void fingerprintCoversFundamentalsAndPointInTimeUniverse() {
        QuantDatasetFingerprint fingerprint = new QuantDatasetFingerprint(); QuantDailyBar bar = bar("600000.SH", "10", "10.2");
        QuantFundamentalSnapshot fundamental = new QuantFundamentalSnapshot(); fundamental.setInstrumentCode("600000.SH");
        fundamental.setReportPeriod(LocalDate.of(2024, 3, 31)); fundamental.setDisclosedAt(LocalDate.of(2024, 4, 30));
        fundamental.setRoe(new BigDecimal("0.12"));
        QuantUniverseMember member = new QuantUniverseMember(); member.setTradeDate(bar.getTradeDate());
        member.setInstrumentCode("600000.SH"); member.setMember(true); member.setSourceKind("TEST");
        String complete = fingerprint.dataset(Collections.singletonList(bar), Collections.singletonList(fundamental), Collections.singletonList(member));
        fundamental.setRoe(new BigDecimal("0.13"));
        String changedFundamental = fingerprint.dataset(Collections.singletonList(bar), Collections.singletonList(fundamental), Collections.singletonList(member));
        member.setMember(false);
        String changedUniverse = fingerprint.dataset(Collections.singletonList(bar), Collections.singletonList(fundamental), Collections.singletonList(member));
        assertNotEquals(complete, changedFundamental); assertNotEquals(changedFundamental, changedUniverse);
    }

    @Test
    void rejectsInvalidOhlcBeforeWritingAnything() {
        QuantDatasetRepository datasets = mock(QuantDatasetRepository.class);
        QuantMarketDataRepository marketData = mock(QuantMarketDataRepository.class);
        QuantDataset value = new QuantDataset();
        value.setId(1L); value.setRevision(0); value.setStatus("EMPTY");
        when(datasets.findById(1L)).thenReturn(java.util.Optional.of(value));
        QuantDatasetService service = new QuantDatasetService();
        ReflectionTestUtils.setField(service, "datasets", datasets);
        ReflectionTestUtils.setField(service, "marketData", marketData);
        ReflectionTestUtils.setField(service, "fingerprint", new QuantDatasetFingerprint());
        ReflectionTestUtils.setField(service, "quality", new QuantDataQualityService());
        QuantDailyBar invalid = bar("600000.SH", "10.00", "10.20");
        invalid.setHigh(new BigDecimal("9.00"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.importBars(1L, Collections.singletonList(invalid)));

        assertEquals("日行情存在非法 OHLC", error.getMessage());
        verify(marketData, never()).insertBars(any());
    }

    private QuantDailyBar bar(String code, String open, String close) {
        QuantDailyBar value = new QuantDailyBar();
        value.setDatasetId(1L); value.setInstrumentCode(code);
        value.setTradeDate(LocalDate.of(2024, 5, 6));
        value.setOpen(new BigDecimal(open)); value.setClose(new BigDecimal(close));
        value.setHigh(value.getOpen().max(value.getClose()).add(BigDecimal.ONE));
        value.setLow(value.getOpen().min(value.getClose()).subtract(BigDecimal.ONE));
        value.setAdjustedClose(value.getClose()); value.setVolume(new BigDecimal("1000"));
        value.setAmount(new BigDecimal("100000")); value.setTradeStatus("TRADING");
        return value;
    }
}
