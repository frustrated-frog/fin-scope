package com.finscope.service.learningcard;

import com.finscope.common.enums.financials.*;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.fundamentals.FundamentalResearchRepository;
import com.finscope.domain.financials.*;
import com.finscope.domain.fundamentals.CompanyBusinessProfile;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.research.material.CompanyBusinessProfileClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockLearningBaselineServiceTest {
    private StockLearningBaselineService service;
    private FundamentalResearchRepository profiles;
    private CompanyBusinessProfileClient client;
    private FinancialReportRepository reports;
    private Instrument stock;

    @BeforeEach
    void setUp() {
        service = new StockLearningBaselineService();
        profiles = mock(FundamentalResearchRepository.class);
        client = mock(CompanyBusinessProfileClient.class);
        reports = mock(FinancialReportRepository.class);
        ReflectionTestUtils.setField(service, "profiles", profiles);
        ReflectionTestUtils.setField(service, "profileClient", client);
        ReflectionTestUtils.setField(service, "reports", reports);
        stock = new Instrument();
        stock.setId(1L);
        stock.setCode("605058");
        stock.setName("澳弘电子");
        stock.setMarket("SH");
    }

    @Test
    void fetchesIdentityVerifiedProfileWhenThereIsNoLocalProfile() {
        when(client.fetch(stock)).thenReturn(profile(1L));
        var result = service.build(stock);
        assertEquals(1, result.size());
        assertTrue(result.get(0).content().contains("澳弘电子"));
        assertTrue(result.get(0).content().contains("605058"));
        assertTrue(result.get(0).content().contains("印制电路板"));
        verify(profiles).saveProfile(any());
    }

    @Test
    void rejectsCachedProfileForAnotherInstrument() {
        when(profiles.profile(1L)).thenReturn(Optional.of(profile(2L)));
        when(client.fetch(stock)).thenThrow(new IllegalStateException("provider unavailable"));
        assertTrue(service.build(stock).isEmpty());
    }

    @Test
    void separatesFinancialSourcesAndIncludesYearToDateCashFlowWithoutQuarterlyOrUnknownValues() {
        when(profiles.profile(1L)).thenReturn(Optional.of(profile(1L)));
        FinancialReport report = report(7L, 1L, LocalDate.now().minusMonths(1));
        when(reports.findReports(1L)).thenReturn(List.of(report));
        var revenue = item("REVENUE", "1000000", "FUYAO_TONGHUASHUN_API", "CURRENT_YTD");
        var cash = item("OPERATING_CASH_FLOW", "800000", "SINA_FINANCIAL_STATEMENTS", "CURRENT_YTD");
        var quarterly = item("REVENUE", "100", "FUYAO_TONGHUASHUN_API", "CURRENT_QUARTER");
        var unknown = item(null, "12345", "FUYAO_TONGHUASHUN_API", "CURRENT_YTD");
        when(reports.findLineItems(7L, FinancialStatementType.INCOME)).thenReturn(List.of(revenue, quarterly, unknown));
        when(reports.findLineItems(7L, FinancialStatementType.CASH_FLOW)).thenReturn(List.of(cash));

        var result = service.build(stock);
        assertEquals(3, result.size());
        assertTrue(result.get(1).content().contains("1000000 CNY"));
        assertFalse(result.get(1).content().contains("800000"));
        assertFalse(result.get(1).content().contains("CURRENT_QUARTER"));
        assertTrue(result.get(2).content().contains("800000 CNY"));
        assertTrue(result.get(2).getUrl().contains("sina.com.cn"));
        assertTrue(result.get(1).getUrl().contains("10jqka.com.cn/605058"));
    }

    @Test
    void neverUsesAnotherCompanyUnverifiedOrFutureReport() {
        when(profiles.profile(1L)).thenReturn(Optional.of(profile(1L)));
        var wrongStock = report(7L, 2L, LocalDate.now().minusMonths(1));
        var future = report(8L, 1L, LocalDate.now().plusYears(1));
        var conflict = report(9L, 1L, LocalDate.now().minusMonths(2));
        conflict.setQualityStatus(FinancialQualityStatus.CONFLICT);
        when(reports.findReports(1L)).thenReturn(List.of(wrongStock, future, conflict));
        assertEquals(1, service.build(stock).size());
        verify(reports, never()).findLineItems(anyLong(), any());
    }

    private CompanyBusinessProfile profile(Long instrumentId) {
        var result = new CompanyBusinessProfile();
        result.setInstrumentId(instrumentId);
        result.setCompanyName("常州澳弘电子股份有限公司");
        result.setDescription("主营印制电路板研发、生产和销售");
        result.setSourceUrl("https://example.com/605058");
        result.setFetchedAt(LocalDateTime.now());
        return result;
    }

    private FinancialReport report(Long id, Long instrumentId, LocalDate period) {
        var result = new FinancialReport();
        result.setId(id);
        result.setInstrumentId(instrumentId);
        result.setPeriodEnd(period);
        result.setReportType(FinancialReportType.Q3);
        result.setScope("CONSOLIDATED");
        result.setCurrency("CNY");
        result.setSourceCode("FUYAO_TONGHUASHUN_API");
        result.setQualityStatus(FinancialQualityStatus.STALE);
        return result;
    }

    private FinancialLineItem item(String concept, String value, String source, String role) {
        var result = new FinancialLineItem();
        result.setSourceLabel(concept);
        result.setConceptCode(concept);
        result.setSourceCode(source);
        result.setNormalizedValue(new BigDecimal(value));
        result.setCurrency("CNY");
        result.setPeriodRole(role);
        result.setQualityStatus(FinancialQualityStatus.FRESH);
        return result;
    }
}
