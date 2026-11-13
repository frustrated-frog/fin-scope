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
        ReflectionTestUtils.setField(service, "instruments", instruments);
        ReflectionTestUtils.setField(service, "reports", reports);
        ReflectionTestUtils.setField(service, "financials", financials);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "reactions", reactions);
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
