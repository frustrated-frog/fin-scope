package com.finscope.service.instrument;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockFinancialQuarter;
import com.finscope.domain.instrument.StockFundamentals;
import com.finscope.domain.valuation.StockValuationSnapshot;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.service.financials.FinancialRefreshService;
import com.finscope.service.valuation.StockValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockFundamentalsServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-06T02:00:00Z");
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private InstrumentRepository instruments;
    private FinancialRefreshService financialRefresh;
    private StockFinancialQuarterAssembler quarters;
    private StockValuationService valuations;
    private StockFundamentalsService service;
    private Instrument stock;
    private StockValuationView local;

    @BeforeEach
    void setUp() {
        instruments = mock(InstrumentRepository.class);
        financialRefresh = mock(FinancialRefreshService.class);
        quarters = mock(StockFinancialQuarterAssembler.class);
        valuations = mock(StockValuationService.class);
        service = new StockFundamentalsService();
        ReflectionTestUtils.setField(service, "instruments", instruments);
        ReflectionTestUtils.setField(service, "financialRefresh", financialRefresh);
        ReflectionTestUtils.setField(service, "quarters", quarters);
        ReflectionTestUtils.setField(service, "valuations", valuations);
        ReflectionTestUtils.setField(service, "peers", mock(StockFundamentalsPeerService.class));
        setTime(NOW);
        stock = new Instrument();
        stock.setId(17L);
        stock.setCode("600519");
        stock.setMarket("SH");
        stock.setType("STOCK");
        when(instruments.findByCodeAndType("600519", "STOCK")).thenReturn(Optional.of(stock));
        when(quarters.assemble(eq(17L), anyList())).thenReturn(Collections.emptyList());
        when(quarters.hasUsableReport(eq(17L), any())).thenReturn(true);
        local = new StockValuationView();
        local.setInstrument(stock);
        StockValuationSnapshot snapshot = new StockValuationSnapshot();
        snapshot.setObservedDate(LocalDate.of(2026, 11, 6));
        local.setLatest(snapshot);
        local.getHistory().add(snapshot);
        when(valuations.view(17L)).thenReturn(local);
        when(valuations.refresh(17L)).thenReturn(local);
    }

    @Test
    void readsExistingRawQuartersAndActualValuationWithoutRefetchingToday() {
        StockFundamentals result = service.load(" 600519 ", false);

        assertSame(stock, result.getInstrument());
        assertSame(local, result.getValuation());
        assertSame(local.getHistory(), result.getValuation().getHistory());
        assertEquals(NOW, result.getRefreshedAt());
        verifyNoInteractions(financialRefresh);
        verify(valuations, never()).refresh(anyLong());
        ArgumentCaptor<List<LocalDate>> dates = ArgumentCaptor.forClass(List.class);
        verify(quarters, atLeastOnce()).assemble(eq(17L), dates.capture());
        assertEquals(List.of(LocalDate.of(2024, 12, 31), LocalDate.of(2025, 3, 31),
                LocalDate.of(2025, 6, 30), LocalDate.of(2025, 9, 30), LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 9, 30)),
                dates.getValue());
    }

    @Test
    void fillsMissingPeriodsIndependentlyAndIncludesPriorNeededForFirstPoint() {
        when(quarters.hasUsableReport(eq(17L), any())).thenReturn(false);
        doThrow(new ProviderContractException("OFFLINE", "offline", true))
                .when(financialRefresh).refresh(17L, LocalDate.of(2026, 9, 30), FinancialReportType.Q3);

        StockFundamentals result = service.load("600519", false);

        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        verify(financialRefresh, times(9)).refresh(eq(17L), dates.capture(), any());
        assertEquals(LocalDate.of(2026, 9, 30), dates.getAllValues().get(0));
        assertEquals(LocalDate.of(2024, 9, 30), dates.getAllValues().get(8));
        assertTrue(result.getWarnings().stream().anyMatch(value -> value.contains("2026-09-30")));
        verify(financialRefresh).refresh(17L, LocalDate.of(2025, 12, 31), FinancialReportType.ANNUAL);
        verify(financialRefresh).refresh(17L, LocalDate.of(2026, 6, 30), FinancialReportType.HALF_YEAR);
    }

    @Test
    void failedSourceChangeRetainsCompletePriorQuarterWhileSuccessfulPeriodsUpdate() {
        LocalDate failedPeriod = LocalDate.of(2026, 9, 30);
        LocalDate successfulPeriod = LocalDate.of(2026, 6, 30);
        StockFinancialQuarter priorSuccessful = quarter(successfulPeriod, 71L, "SOURCE_A", "100");
        StockFinancialQuarter priorFailed = quarter(failedPeriod, 81L, "SOURCE_A", "120");
        priorFailed.setNetProfitParent(new BigDecimal("-5"));
        priorFailed.setOperatingCashFlow(new BigDecimal("-20"));
        priorFailed.setGrossMarginPct(new BigDecimal("35"));
        priorFailed.getDataGaps().add("原报告披露提示");
        StockFinancialQuarter updatedSuccessful = quarter(successfulPeriod, 171L, "SOURCE_B", "210");
        StockFinancialQuarter brokenFailed = quarter(failedPeriod, 181L, "SOURCE_B", null);
        brokenFailed.setCurrency("USD");
        brokenFailed.setNetProfitParent(new BigDecimal("999"));
        brokenFailed.getDataGaps().add("新报告来源与旧行不匹配");
        AtomicReference<List<StockFinancialQuarter>> stored = new AtomicReference<List<StockFinancialQuarter>>(
                List.of(priorSuccessful, priorFailed));
        when(quarters.assemble(eq(17L), anyList())).thenAnswer(invocation -> stored.get());
        when(financialRefresh.refresh(17L, failedPeriod, FinancialReportType.Q3)).thenAnswer(invocation -> {
            stored.set(List.of(updatedSuccessful, brokenFailed));
            throw new IllegalStateException("header saved before line write failed");
        });

        StockFundamentals result = service.load("600519", true);

        assertSame(updatedSuccessful, result.getQuarters().get(0));
        StockFinancialQuarter retained = result.getQuarters().get(1);
        assertNotSame(priorFailed, retained);
        assertEquals(priorFailed.getPeriodEnd(), retained.getPeriodEnd());
        assertEquals(priorFailed.getReportType(), retained.getReportType());
        assertEquals(priorFailed.getReportId(), retained.getReportId());
        assertEquals(priorFailed.getPublishedAt(), retained.getPublishedAt());
        assertEquals(priorFailed.getSourceCode(), retained.getSourceCode());
        assertEquals(priorFailed.getCurrency(), retained.getCurrency());
        assertEquals(priorFailed.getQualityStatus(), retained.getQualityStatus());
        assertEquals(priorFailed.getRevenue(), retained.getRevenue());
        assertEquals(priorFailed.getNetProfitParent(), retained.getNetProfitParent());
        assertEquals(priorFailed.getOperatingCashFlow(), retained.getOperatingCashFlow());
        assertEquals(priorFailed.getGrossMarginPct(), retained.getGrossMarginPct());
        assertTrue(retained.getDataGaps().contains("原报告披露提示"));
        assertTrue(retained.getDataGaps().stream().anyMatch(gap -> gap.contains("保留刷新前完整季度快照")));
        assertFalse(retained.getDataGaps().contains("新报告来源与旧行不匹配"));
        assertEquals(List.of("原报告披露提示"), priorFailed.getDataGaps());
        assertSame(result, service.load("600519", false));
    }

    @Test
    void failedHalfYearPrerequisiteRetainsWholePriorQ3AfterSuccessfulQ3ReadbackDegrades() {
        LocalDate halfYear = LocalDate.of(2026, 6, 30);
        LocalDate q3 = LocalDate.of(2026, 9, 30);
        StockFinancialQuarter previousHalfYear = quarter(halfYear, 71L, "SOURCE_A", "100");
        StockFinancialQuarter previousQ3 = quarter(q3, 81L, "SOURCE_A", "120");
        previousQ3.setNetProfitParent(new BigDecimal("-5"));
        previousQ3.setOperatingCashFlow(new BigDecimal("-20"));
        previousQ3.setGrossMarginPct(new BigDecimal("35"));
        previousQ3.getDataGaps().add("旧季度披露提示");
        StockFinancialQuarter successfulQ3 = quarter(q3, 181L, "SOURCE_B", "210");
        StockFinancialQuarter brokenHalfYear = quarter(halfYear, 171L, "SOURCE_B", null);
        StockFinancialQuarter degradedQ3 = quarter(q3, 181L, "SOURCE_B", null);
        degradedQ3.setCurrency("USD");
        degradedQ3.getDataGaps().add("缺少同来源前序累计值");
        AtomicReference<List<StockFinancialQuarter>> stored = new AtomicReference<List<StockFinancialQuarter>>(
                List.of(previousHalfYear, previousQ3));
        when(quarters.assemble(eq(17L), anyList())).thenAnswer(invocation -> stored.get());
        when(financialRefresh.refresh(17L, q3, FinancialReportType.Q3)).thenAnswer(invocation -> {
            stored.set(List.of(previousHalfYear, successfulQ3));
            return null;
        });
        when(financialRefresh.refresh(17L, halfYear, FinancialReportType.HALF_YEAR)).thenAnswer(invocation -> {
            stored.set(List.of(brokenHalfYear, degradedQ3));
            throw new IllegalStateException("half-year header saved before line write failed");
        });

        StockFundamentals result = service.load("600519", true);

        org.mockito.InOrder order = inOrder(financialRefresh);
        order.verify(financialRefresh).refresh(17L, q3, FinancialReportType.Q3);
        order.verify(financialRefresh).refresh(17L, halfYear, FinancialReportType.HALF_YEAR);
        StockFinancialQuarter retained = result.getQuarters().get(1);
        assertNotSame(previousQ3, retained);
        assertEquals(previousQ3.getPeriodEnd(), retained.getPeriodEnd());
        assertEquals(previousQ3.getReportType(), retained.getReportType());
        assertEquals(previousQ3.getReportId(), retained.getReportId());
        assertEquals(previousQ3.getPublishedAt(), retained.getPublishedAt());
        assertEquals(previousQ3.getQualityStatus(), retained.getQualityStatus());
        assertEquals(previousQ3.getSourceCode(), retained.getSourceCode());
        assertEquals(previousQ3.getCurrency(), retained.getCurrency());
        assertEquals(previousQ3.getRevenue(), retained.getRevenue());
        assertEquals(previousQ3.getNetProfitParent(), retained.getNetProfitParent());
        assertEquals(previousQ3.getOperatingCashFlow(), retained.getOperatingCashFlow());
        assertEquals(previousQ3.getGrossMarginPct(), retained.getGrossMarginPct());
        assertEquals("SOURCE_A", result.getQuarters().get(0).getSourceCode());
        assertTrue(retained.getDataGaps().contains("旧季度披露提示"));
        assertTrue(retained.getDataGaps().stream().anyMatch(gap -> gap.contains("前序累计报告 2026-06-30")
                && gap.contains("保留刷新前完整季度快照")));
        assertFalse(retained.getDataGaps().contains("缺少同来源前序累计值"));
        assertEquals(List.of("旧季度披露提示"), previousQ3.getDataGaps());
    }

    @Test
    void failedPrerequisiteDoesNotMaskSuccessfulCompleteDependentQuarterFacts() {
        LocalDate halfYear = LocalDate.of(2026, 6, 30);
        LocalDate q3 = LocalDate.of(2026, 9, 30);
        StockFinancialQuarter previous = quarter(q3, 81L, "SOURCE_A", "120");
        previous.setNetProfitParent(new BigDecimal("5"));
        previous.setOperatingCashFlow(new BigDecimal("20"));
        previous.setGrossMarginPct(new BigDecimal("35"));
        StockFinancialQuarter updated = quarter(q3, 181L, "SOURCE_B", "210");
        updated.setNetProfitParent(new BigDecimal("8"));
        updated.setOperatingCashFlow(new BigDecimal("25"));
        updated.setGrossMarginPct(new BigDecimal("40"));
        when(quarters.assemble(eq(17L), anyList())).thenReturn(List.of(previous), List.of(updated));
        when(financialRefresh.refresh(17L, halfYear, FinancialReportType.HALF_YEAR))
                .thenThrow(new IllegalStateException("analysis failed after valid raw lines saved"));

        StockFundamentals result = service.load("600519", true);

        assertSame(updated, result.getQuarters().get(0));
        assertTrue(updated.getDataGaps().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("2026-06-30")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVENUE", "NET_PROFIT_PARENT", "OPERATING_CASH_FLOW", "GROSS_MARGIN"})
    void anyLostFactAfterPrerequisiteFailureRetainsWholePointInsteadOfMixingFields(String missingFact) {
        LocalDate halfYear = LocalDate.of(2026, 6, 30);
        LocalDate q3 = LocalDate.of(2026, 9, 30);
        StockFinancialQuarter previous = quarter(q3, 81L, "SOURCE_A", "120");
        previous.setNetProfitParent(new BigDecimal("5"));
        previous.setOperatingCashFlow(new BigDecimal("20"));
        previous.setGrossMarginPct(new BigDecimal("35"));
        StockFinancialQuarter updated = quarter(q3, 181L, "SOURCE_B", "210");
        updated.setNetProfitParent(new BigDecimal("8"));
        updated.setOperatingCashFlow(new BigDecimal("25"));
        updated.setGrossMarginPct(new BigDecimal("40"));
        switch (missingFact) {
            case "REVENUE" -> updated.setRevenue(null);
            case "NET_PROFIT_PARENT" -> updated.setNetProfitParent(null);
            case "OPERATING_CASH_FLOW" -> updated.setOperatingCashFlow(null);
            case "GROSS_MARGIN" -> updated.setGrossMarginPct(null);
            default -> throw new IllegalArgumentException(missingFact);
        }
        when(quarters.assemble(eq(17L), anyList())).thenReturn(List.of(previous), List.of(updated));
        when(financialRefresh.refresh(17L, halfYear, FinancialReportType.HALF_YEAR))
                .thenThrow(new IllegalStateException("half-year raw write failed"));

        StockFinancialQuarter retained = service.load("600519", true).getQuarters().get(0);

        assertEquals(previous.getReportId(), retained.getReportId());
        assertEquals(previous.getSourceCode(), retained.getSourceCode());
        assertEquals(previous.getCurrency(), retained.getCurrency());
        assertEquals(previous.getRevenue(), retained.getRevenue());
        assertEquals(previous.getNetProfitParent(), retained.getNetProfitParent());
        assertEquals(previous.getOperatingCashFlow(), retained.getOperatingCashFlow());
        assertEquals(previous.getGrossMarginPct(), retained.getGrossMarginPct());
        assertTrue(retained.getDataGaps().stream().anyMatch(gap -> gap.contains("前序累计报告 2026-06-30")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-10", "0"})
    void undefinedMarginForSuccessfulNonpositiveRevenueDoesNotRollBackNewQuarterFacts(String revenue) {
        LocalDate halfYear = LocalDate.of(2026, 6, 30);
        LocalDate q3 = LocalDate.of(2026, 9, 30);
        StockFinancialQuarter previous = quarter(q3, 81L, "SOURCE_A", "120");
        previous.setNetProfitParent(new BigDecimal("5"));
        previous.setOperatingCashFlow(new BigDecimal("20"));
        previous.setGrossMarginPct(new BigDecimal("35"));
        StockFinancialQuarter updated = quarter(q3, 181L, "SOURCE_B", revenue);
        updated.setNetProfitParent(new BigDecimal("-8"));
        updated.setOperatingCashFlow(new BigDecimal("-25"));
        updated.getDataGaps().add("毛利率：单季度营业收入不大于0，无法计算");
        when(quarters.assemble(eq(17L), anyList())).thenReturn(List.of(previous), List.of(updated));
        when(financialRefresh.refresh(17L, halfYear, FinancialReportType.HALF_YEAR))
                .thenThrow(new IllegalStateException("analysis failed after valid raw lines saved"));

        StockFinancialQuarter point = service.load("600519", true).getQuarters().get(0);

        assertSame(updated, point);
        assertEquals(new BigDecimal(revenue), point.getRevenue());
        assertNull(point.getGrossMarginPct());
    }

    @Test
    void failedAnnualPrerequisiteDoesNotRollBackIndependentQ1OrNonadjacentQ3() {
        LocalDate annual = LocalDate.of(2025, 12, 31);
        LocalDate q1 = LocalDate.of(2026, 3, 31);
        LocalDate q3 = LocalDate.of(2026, 9, 30);
        StockFinancialQuarter previousQ1 = quarter(q1, 61L, "SOURCE_A", "100");
        StockFinancialQuarter previousQ3 = quarter(q3, 81L, "SOURCE_A", "120");
        StockFinancialQuarter updatedQ1 = quarter(q1, 161L, "SOURCE_B", null);
        StockFinancialQuarter updatedQ3 = quarter(q3, 181L, "SOURCE_B", null);
        when(quarters.assemble(eq(17L), anyList())).thenReturn(
                List.of(previousQ1, previousQ3), List.of(updatedQ1, updatedQ3));
        when(financialRefresh.refresh(17L, annual, FinancialReportType.ANNUAL))
                .thenThrow(new IllegalStateException("annual raw write failed"));

        StockFundamentals result = service.load("600519", true);

        assertSame(updatedQ1, result.getQuarters().get(0));
        assertSame(updatedQ3, result.getQuarters().get(1));
    }

    @Test
    void readbackAssemblyFailureKeepsWholePriorSnapshotWithExplicitGapsAndCachesIt() {
        StockFinancialQuarter prior = quarter(LocalDate.of(2026, 9, 30), 81L, "SOURCE_A", "120");
        StockFinancialQuarter unavailable = quarter(LocalDate.of(2026, 6, 30), 71L, "SOURCE_A", null);
        when(quarters.assemble(eq(17L), anyList())).thenReturn(List.of(unavailable, prior))
                .thenThrow(new IllegalStateException("readback unavailable"));

        StockFundamentals result = service.load("600519", true);

        assertEquals(2, result.getQuarters().size());
        assertSame(unavailable, result.getQuarters().get(0));
        StockFinancialQuarter retained = result.getQuarters().get(1);
        assertEquals(prior.getRevenue(), retained.getRevenue());
        assertEquals(prior.getSourceCode(), retained.getSourceCode());
        assertEquals(prior.getCurrency(), retained.getCurrency());
        assertEquals(prior.getReportId(), retained.getReportId());
        assertTrue(retained.getDataGaps().stream().anyMatch(gap -> gap.contains("刷新后财报读取失败")));
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("保留刷新前完整季度快照")));
        assertTrue(prior.getDataGaps().isEmpty());
        assertSame(result, service.load("600519", false));
        verify(quarters, times(2)).assemble(eq(17L), anyList());
    }

    @Test
    void failedPeriodWithoutPriorFactsKeepsReadbackPointInsteadOfOldEmptyMetadata() {
        LocalDate period = LocalDate.of(2026, 9, 30);
        StockFinancialQuarter previousEmpty = quarter(period, 81L, "SOURCE_A", null);
        StockFinancialQuarter newPartial = quarter(period, 181L, "SOURCE_B", "120");
        when(quarters.assemble(eq(17L), anyList())).thenReturn(List.of(previousEmpty), List.of(newPartial));
        when(financialRefresh.refresh(17L, period, FinancialReportType.Q3))
                .thenThrow(new IllegalStateException("analysis failed after raw lines saved"));

        StockFundamentals result = service.load("600519", true);

        assertSame(newPartial, result.getQuarters().get(0));
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("2026-09-30")));
    }

    @Test
    void cachesFailedAutomaticAttemptsAndRetriesAfterFiveMinutes() {
        when(quarters.hasUsableReport(eq(17L), any())).thenReturn(false);
        when(financialRefresh.refresh(eq(17L), any(), any()))
                .thenThrow(new ProviderContractException("OFFLINE", "offline", true));

        StockFundamentals first = service.load("600519", false);
        assertSame(first, service.load("600519", false));
        verify(financialRefresh, times(9)).refresh(eq(17L), any(), any());
        setTime(NOW.plus(Duration.ofMinutes(5)));
        assertNotSame(first, service.load("600519", false));
        verify(financialRefresh, times(18)).refresh(eq(17L), any(), any());
    }

    @Test
    void triesStaleValuationOncePerChinaDayAndRetainsGenuineHistoryOnFailure() {
        local.getLatest().setObservedDate(LocalDate.of(2026, 11, 5));
        when(valuations.refresh(17L)).thenThrow(new ProviderContractException("OFFLINE", "offline", true));

        StockFundamentals first = service.load("600519", false);
        assertSame(local, first.getValuation());
        assertTrue(first.getWarnings().stream().anyMatch(value -> value.contains("估值刷新失败")));
        setTime(NOW.plus(Duration.ofMinutes(6)));
        StockFundamentals second = service.load("600519", false);
        assertSame(local, second.getValuation());
        assertTrue(second.getWarnings().stream().anyMatch(value -> value.contains("估值刷新失败")));
        verify(valuations, times(1)).refresh(17L);
        setTime(Instant.parse("2026-11-06T16:01:00Z"));
        service.load("600519", false);
        verify(valuations, times(2)).refresh(17L);
    }

    @Test
    void retainsSuccessfulValuationWhenQuarterRefreshFailsAndDoesNotInventMissingValuation() {
        local.getLatest().setObservedDate(LocalDate.of(2026, 11, 5));
        StockValuationView updated = new StockValuationView();
        updated.setInstrument(stock);
        updated.setHistory(local.getHistory());
        when(valuations.refresh(17L)).thenReturn(updated);
        when(quarters.hasUsableReport(eq(17L), any())).thenReturn(false);
        when(financialRefresh.refresh(eq(17L), any(), any()))
                .thenThrow(new ProviderContractException("OFFLINE", "offline", true));
        assertSame(updated, service.load("600519", false).getValuation());

        setTime(NOW.plus(Duration.ofDays(1)));
        when(valuations.view(17L)).thenThrow(new IllegalStateException("local read unavailable"));
        when(valuations.refresh(17L)).thenThrow(new ProviderContractException("OFFLINE", "offline", true));
        StockFundamentals unavailable = service.load("600519", false);
        assertNull(unavailable.getValuation());
        assertTrue(unavailable.getWarnings().stream().anyMatch(value -> value.contains("本地估值暂不可用")));
        assertTrue(unavailable.getWarnings().stream().anyMatch(value -> value.contains("估值刷新失败")));
        assertNotNull(unavailable.getQuarters());
    }

    @Test
    void forceBypassesSnapshotAndDailyValuationGateButDoesNotRefreshExtraLocalPrior() {
        service.load("600519", false);
        StockFundamentals forced = service.load("600519", true);
        assertNotNull(forced);
        verify(financialRefresh, times(8)).refresh(eq(17L), any(), any());
        verify(financialRefresh, never()).refresh(eq(17L), eq(LocalDate.of(2024, 9, 30)), any());
        verify(valuations).refresh(17L);
    }

    @Test
    void outerDeadlineBoundsEveryNestedRefreshAndSkipsRemainingCallsWhenExpired() {
        when(quarters.hasUsableReport(eq(17L), any())).thenReturn(false);
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofNanos(1))) {
            StockFundamentals result = service.load("600519", false);
            assertTrue(result.getWarnings().stream().anyMatch(value -> value.contains("总时限")));
        }
        verifyNoInteractions(financialRefresh);
        verify(valuations, never()).refresh(anyLong());
        assertEquals(Long.MAX_VALUE, ProviderCallDeadline.remainingMillis());
    }

    @Test
    void opensOneSharedSixtySecondDeadlineRatherThanResettingForEveryPeriod() {
        when(quarters.hasUsableReport(eq(17L), any())).thenReturn(false);
        List<Long> remaining = new ArrayList<Long>();
        when(financialRefresh.refresh(eq(17L), any(), any())).thenAnswer(invocation -> {
            long available = ProviderCallDeadline.remainingMillis();
            try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofSeconds(90))) {
                assertTrue(ProviderCallDeadline.remainingMillis() <= available);
            }
            remaining.add(available);
            return null;
        });
        service.load("600519", false);
        assertEquals(9, remaining.size());
        for (int index = 0; index < remaining.size(); index++) {
            assertTrue(remaining.get(index) > 0 && remaining.get(index) <= 60_000L);
            if (index > 0) {
                assertTrue(remaining.get(index) <= remaining.get(index - 1));
            }
        }
        assertEquals(Long.MAX_VALUE, ProviderCallDeadline.remainingMillis());
    }

    @Test
    void singleflightsConcurrentForcedRequestsForSameSymbol() throws Exception {
        CountDownLatch enteredProvider = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(valuations.refresh(17L)).thenAnswer(invocation -> {
            enteredProvider.countDown();
            assertTrue(releaseProvider.await(5, TimeUnit.SECONDS));
            return local;
        });
        CompletableFuture<StockFundamentals> first = new CompletableFuture<StockFundamentals>();
        CompletableFuture<StockFundamentals> second = new CompletableFuture<StockFundamentals>();
        AtomicReference<Throwable> threadError = new AtomicReference<Throwable>();
        Thread firstThread = requestThread(first, threadError);
        Thread secondThread = requestThread(second, threadError);
        firstThread.start();
        try {
            assertTrue(enteredProvider.await(5, TimeUnit.SECONDS));
            secondThread.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (secondThread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertEquals(Thread.State.BLOCKED, secondThread.getState());
        } finally {
            releaseProvider.countDown();
        }
        assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        assertNull(threadError.get());
        verify(valuations, times(1)).refresh(17L);
        verify(financialRefresh, times(8)).refresh(eq(17L), any(), any());
    }

    @Test
    void latestEndedQuarterIncludesEarlyPublishedAnnualAndDoesNotRequestFutureQuarter() {
        setTime(Instant.parse("2026-03-20T00:00:00Z"));
        when(quarters.hasUsableReport(eq(17L), any())).thenReturn(false);
        service.load("600519", false);
        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        verify(financialRefresh, times(8)).refresh(eq(17L), dates.capture(), any());
        assertEquals(LocalDate.of(2025, 12, 31), dates.getAllValues().get(0));
        assertTrue(dates.getAllValues().stream().allMatch(date -> date.isBefore(LocalDate.of(2026, 1, 1))));
    }

    @Test
    void boundsCacheAndLockEntriesAcrossManySymbols() {
        for (int index = 0; index < 270; index++) {
            String code = String.format("%06d", index);
            Instrument instrument = new Instrument();
            instrument.setId(1000L + index);
            instrument.setCode(code);
            instrument.setType("STOCK");
            instrument.setMarket("SH");
            when(instruments.findByCodeAndType(code, "STOCK")).thenReturn(Optional.of(instrument));
            when(quarters.assemble(eq(instrument.getId()), anyList())).thenReturn(Collections.emptyList());
            when(quarters.hasUsableReport(eq(instrument.getId()), any())).thenReturn(true);
            when(valuations.view(instrument.getId())).thenReturn(local);
            service.load(code, false);
        }
        java.util.Map<?, ?> attempts = (java.util.Map<?, ?>) ReflectionTestUtils.getField(service, "attempts");
        assertNotNull(attempts);
        assertEquals(256, attempts.size());
    }

    @Test
    void malformedFundOnlyAndUnsupportedMarketsNeverRequestExternalData() {
        assertThrows(BusinessException.class, () -> service.load("../600519", false));
        assertThrows(BusinessException.class, () -> service.load("600519.SH", false));
        when(instruments.findByCodeAndType("021894", "STOCK")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.load("021894", true));
        stock.setMarket("US");
        assertThrows(BusinessException.class, () -> service.load("600519", false));
        verifyNoInteractions(financialRefresh, quarters, valuations);
    }

    private Thread requestThread(CompletableFuture<StockFundamentals> target,
                                 AtomicReference<Throwable> error) {
        return new Thread(() -> {
            try {
                target.complete(service.load("600519", true));
            } catch (Throwable failure) {
                error.set(failure);
                target.completeExceptionally(failure);
            }
        }, "stock-fundamentals-test");
    }

    private void setTime(Instant time) {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(time, CHINA));
    }

    private static StockFinancialQuarter quarter(LocalDate period, Long reportId, String source, String revenue) {
        StockFinancialQuarter point = new StockFinancialQuarter();
        point.setPeriodEnd(period);
        point.setReportType(switch (period.getMonthValue()) {
            case 3 -> FinancialReportType.Q1;
            case 6 -> FinancialReportType.HALF_YEAR;
            case 9 -> FinancialReportType.Q3;
            default -> FinancialReportType.ANNUAL;
        });
        point.setReportId(reportId);
        point.setSourceCode(source);
        point.setCurrency("CNY");
        point.setQualityStatus(FinancialQualityStatus.FRESH);
        point.setPublishedAt(LocalDateTime.of(2026, 10, 28, 0, 0));
        point.setRevenue(revenue == null ? null : new BigDecimal(revenue));
        return point;
    }
}
