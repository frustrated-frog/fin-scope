package com.finscope.service.fundamentals;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.enums.investmentobservation.ReactionEventSubtype;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.fundamentals.FundamentalResearchRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.fundamentals.CompanyBusinessNotes;
import com.finscope.domain.fundamentals.CompanyBusinessProfile;
import com.finscope.domain.fundamentals.FundamentalWatch;
import com.finscope.domain.fundamentals.FundamentalWatchReviewCommand;
import com.finscope.domain.fundamentals.FundamentalFinancialBaseline;
import com.finscope.domain.fundamentals.FundamentalMetric;
import com.finscope.common.enums.fundamentals.FundamentalDimension;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.common.enums.financials.FinancialStatementType;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.rpc.research.material.CompanyBusinessProfileClient;
import com.finscope.service.financials.FinancialQueryService;
import com.finscope.service.instrument.StockFundamentalsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FundamentalResearchServiceTest {
    private FundamentalResearchService service;
    private InstrumentRepository instruments;
    private FinancialReportRepository reports;
    private FinancialQueryService financials;
    private FundamentalResearchRepository repository;
    private ReactionSampleRepository reactions;
    private CompanyBusinessProfileClient profiles;
    private StockFundamentalsService fundamentals;
    private Instrument stock;

    @BeforeEach
    void setUp() {
        service = new FundamentalResearchService();
        instruments = mock(InstrumentRepository.class);
        reports = mock(FinancialReportRepository.class);
        financials = mock(FinancialQueryService.class);
        repository = mock(FundamentalResearchRepository.class);
        reactions = mock(ReactionSampleRepository.class);
        profiles = mock(CompanyBusinessProfileClient.class);
        fundamentals = mock(StockFundamentalsService.class);
        var resolver = new FundamentalStockResolver();
        ReflectionTestUtils.setField(resolver, "instruments", instruments);
        ReflectionTestUtils.setField(service, "stocks", resolver);
        var materials = new FundamentalResearchMaterialService();
        ReflectionTestUtils.setField(materials, "reports", reports);
        ReflectionTestUtils.setField(materials, "financials", financials);
        ReflectionTestUtils.setField(materials, "reactions", reactions);
        ReflectionTestUtils.setField(materials, "clock", Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(service, "materials", materials);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "profiles", profiles);
        ReflectionTestUtils.setField(service, "fundamentals", fundamentals);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
        stock = new Instrument();
        stock.setId(5L);
        stock.setCode("603618");
        stock.setName("杭电股份");
        stock.setMarket("SH");
        when(instruments.findByCodeAndType("603618", "STOCK")).thenReturn(Optional.of(stock));
        when(repository.profile(5L)).thenReturn(Optional.empty());
        when(repository.notes(5L)).thenReturn(new CompanyBusinessNotes());
    }

    @Test
    void readUsesOnlyLocalDataAndExcludesFutureParentAndConflictingReports() {
        var future = report(1, "2026-12-31");
        var parent = report(2, "2026-09-30");
        parent.setScope("PARENT");
        var conflict = report(3, "2026-09-30");
        conflict.setQualityStatus(FinancialQualityStatus.CONFLICT);
        var latest = report(4, "2026-09-30");
        var prior = report(5, "2025-09-30");
        when(reports.findReports(5L)).thenReturn(List.of(future, parent, conflict, latest, prior));
        when(financials.view(anyLong())).thenAnswer(call -> {
            var view = new FinancialReportView();
            view.setReport(call.getArgument(0).equals(4L) ? latest : prior);
            return view;
        });

        var result = service.view("603618");

        assertEquals(4L, result.getReportId());
        assertEquals(4, result.getInsights().size());
        verify(financials).view(4L);
        verify(financials).view(5L);
        verifyNoInteractions(profiles, fundamentals);
        verify(repository, never()).saveProfile(any());
    }

    @Test
    void failedRefreshKeepsLocalProfileNotesAndWatchesAndDoesNotEraseAnything() {
        var profile = new CompanyBusinessProfile();
        profile.setCompanyName("已有公司资料");
        when(repository.profile(5L)).thenReturn(Optional.of(profile));
        var watch = new FundamentalWatch();
        watch.setOriginKey("MANUAL:one");
        when(repository.watches(5L)).thenReturn(List.of(watch));
        when(profiles.fetch(stock)).thenThrow(new IllegalStateException("offline"));
        when(fundamentals.loadQuartersForResearch("603618", true)).thenThrow(new IllegalStateException("offline"));

        var result = service.refresh("603618");

        assertEquals(profile, result.getProfile());
        assertEquals(List.of(watch), result.getWatches());
        assertEquals(2, result.getNotices().size());
        verify(repository, never()).saveProfile(any());
        verify(repository, never()).saveNotes(any());
        verify(repository, never()).addWatch(any());
    }

    @Test
    void financingAndHolderDisclosuresRemainDifferentAndOnlyUnsavedEventsAreSuggested() {
        var financing = event(1, ReactionEventSubtype.REPURCHASE_FINANCING);
        var holders = event(2, ReactionEventSubtype.REPURCHASE_SHAREHOLDERS);
        when(reactions.recentForStock("603618", "SH")).thenReturn(List.of(financing, holders));
        var saved = new FundamentalWatch();
        saved.setOriginKey("EVENT:1");
        when(repository.watches(5L)).thenReturn(List.of(saved));

        var result = service.view("603618");

        assertTrue(result.getEvents().get(0).getImpact().contains("不等于实际借款"));
        assertTrue(result.getEvents().get(1).getImpact().contains("不能据此推断"));
        assertEquals(List.of("EVENT:2"), result.getSuggestions().stream().map(FundamentalWatch::getOriginKey).toList());
        assertEquals(LocalDate.parse("2026-12-14"), result.getSuggestions().get(0).getNextReviewDate());
    }

    @Test
    void laterFinancialFactsAreComparedAndStaleProofCannotBeSaved() {
        var watch = new FundamentalWatch();
        watch.setId(7L);
        watch.setOriginKey("FINANCIAL:3:EARNINGS_QUALITY");
        watch.setNextReviewDate(LocalDate.parse("2026-11-14"));
        var baseline = new FundamentalFinancialBaseline();
        baseline.setReportId(3L);
        baseline.setPeriodEnd(LocalDate.parse("2025-09-30"));
        baseline.setReportType("Q3");
        baseline.setScope("CONSOLIDATED");
        baseline.setCurrency("CNY");
        baseline.setSourceCode("SOURCE");
        baseline.setDimension(FundamentalDimension.EARNINGS_QUALITY);
        var metric = new FundamentalMetric();
        metric.setConceptCode("OPERATING_CASH_FLOW");
        metric.setUnit("CNY");
        metric.setValue(new BigDecimal("-10"));
        baseline.setMetrics(List.of(metric));
        watch.setBaselineSnapshot(baseline);
        when(repository.watches(5L)).thenReturn(List.of(watch));
        var report = report(4, "2026-09-30");
        when(reports.findReports(5L)).thenReturn(List.of(report));
        var view = new FinancialReportView();
        view.setReport(report);
        var cash = new FinancialLineItem();
        cash.setConceptCode("OPERATING_CASH_FLOW");
        cash.setCurrency("CNY");
        cash.setSourceCode("SOURCE");
        cash.setNormalizedValue(new BigDecimal("20"));
        cash.setStatementType(FinancialStatementType.CASH_FLOW);
        cash.setPeriodRole("CURRENT_YTD");
        view.getStatements().put(FinancialStatementType.CASH_FLOW, List.of(cash));
        when(financials.view(4L)).thenReturn(view);
        var result = service.view("603618");
        assertTrue(result.getWatches().get(0).isDue());
        String token = result.getWatches().get(0).getComparison().getToken();
        assertNotNull(token);
        var command = new FundamentalWatchReviewCommand();
        command.setStatus(FundamentalWatchStatus.TRACKING);
        command.setNote("对照新披露，继续核对回款");
        command.setNextReviewDate(LocalDate.parse("2027-01-31"));
        command.setComparisonToken(token);
        service.review("603618", 7, command);
        var saved = ArgumentCaptor.forClass(com.finscope.domain.fundamentals.FundamentalWatchReview.class);
        verify(repository).review(eq(5L), eq(7L), eq(0), saved.capture());
        assertEquals(new BigDecimal("30"), saved.getValue().getComparison().getChanges().get(0).getChange());
        assertEquals(command.getNextReviewDate(), saved.getValue().getNextReviewDate());
        cash.setNormalizedValue(new BigDecimal("21"));
        assertThrows(BusinessConflictException.class, () -> service.review("603618", 7, command));
        verify(repository, times(1)).review(anyLong(), anyLong(), anyInt(), any());
        assertEquals(new BigDecimal("-10"), watch.getBaselineSnapshot().getMetrics().get(0).getValue());
    }

    @Test
    void savingFinancialWatchFreezesServerFactsAndRejectsAnOutdatedReportKey() {
        var workspace = new com.finscope.domain.fundamentals.FundamentalResearchWorkspace();
        workspace.setReportId(4L);
        var insight = new com.finscope.domain.fundamentals.FundamentalInsight();
        insight.setDimension(FundamentalDimension.GROWTH);
        workspace.getInsights().add(insight);
        var material = new FundamentalResearchMaterialService();
        assertEquals(4L, material.freeze(workspace, "FINANCIAL:4:GROWTH").getReportId());
        assertThrows(BusinessConflictException.class, () -> material.freeze(workspace, "FINANCIAL:3:GROWTH"));
        assertNull(material.freeze(workspace, "MANUAL:one"));
    }

    @Test
    void rejectsNonStockCodesBeforeAnyExternalCall() {
        assertThrows(BusinessException.class, () -> service.refresh("../600519"));
        stock.setMarket("US");
        assertThrows(BusinessException.class, () -> service.refresh("603618"));
        verifyNoInteractions(profiles, fundamentals);
    }

    private FinancialReport report(long id, String period) {
        var report = new FinancialReport();
        report.setId(id);
        report.setInstrumentId(5L);
        report.setPeriodEnd(LocalDate.parse(period));
        report.setReportType(FinancialReportType.Q3);
        report.setScope("CONSOLIDATED");
        report.setCurrency("CNY");
        report.setSourceCode("SOURCE");
        return report;
    }

    private ReactionSample event(long id, ReactionEventSubtype subtype) {
        var event = new ReactionSample();
        event.setId(id);
        event.setTitle(subtype.name());
        event.setEventType(ReactionEventType.REPURCHASE);
        event.setEventSubtype(subtype);
        return event;
    }
}
