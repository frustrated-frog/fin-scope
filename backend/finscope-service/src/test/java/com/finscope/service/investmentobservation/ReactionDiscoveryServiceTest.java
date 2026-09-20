package com.finscope.service.investmentobservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.dao.investmentobservation.ReactionSchemaMigrator;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.investmentobservation.ReactionStockMatch;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.service.news.NewsWindowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReactionDiscoveryServiceTest {
    @TempDir
    Path temp;
    private ReactionSampleRepository repository;
    private ReactionDiscoveryService service;
    private NewsWindowService materials;
    private ReactionStockResolver resolver;
    private RadarRepository radar;
    private final LocalDateTime now = LocalDateTime.parse("2026-09-20T10:00:00");

    @BeforeEach
    void setup() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + temp.resolve("auto.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        ReactionSchemaMigrator migrator = new ReactionSchemaMigrator();
        ReflectionTestUtils.setField(migrator, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(migrator, "transactionManager", new DataSourceTransactionManager(source));
        migrator.afterPropertiesSet();
        repository = new ReactionSampleRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()));
        service = new ReactionDiscoveryService();
        materials = mock(NewsWindowService.class);
        resolver = mock(ReactionStockResolver.class);
        radar = mock(RadarRepository.class);
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "materials", materials);
        ReflectionTestUtils.setField(service, "resolver", resolver);
        ReflectionTestUtils.setField(service, "radar", radar);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai")));
        when(radar.findActiveSignals(any(), anyInt())).thenReturn(List.of());
        when(resolver.resolve(anyString())).thenReturn(List.of(match("600519.SH", "示例公司")));
    }

    @Test
    void newsAutomaticallyBecomesStockSamplesWithoutMajorEventOrManualConfirmation() {
        ResearchMaterial news = news("示例公司签订重大合同");
        supplyNews(List.of(news));
        RadarSignal duplicate = new RadarSignal();
        duplicate.setTitle(news.getTitle());
        duplicate.setPublishedAt(news.getPublishedAt());
        duplicate.setFirstSeenAt(now.minusMinutes(1));
        when(radar.findActiveSignals(any(), anyInt())).thenReturn(List.of(duplicate));
        when(resolver.resolve(anyString())).thenReturn(List.of(match("600519.SH", "示例公司"), match("000001.SZ", "另一公司")));
        assertEquals(2, service.discover().getResolved());
        List<ReactionSample> samples = repository.recent(Long.MAX_VALUE, 100);
        assertEquals(2, samples.size());
        assertTrue(samples.stream().allMatch(value -> value.isAutomatic() && value.getMajorEventId() == null
                && value.getState() == ReactionSampleState.OBSERVING && value.getPublishedAt().equals(news.getPublishedAt())));
        assertEquals(2, repository.findDue(now, 20).size());
        verifyAutomaticPriceTracking(samples.get(0));
        assertEquals(0, service.discover().getCaptured());
        assertEquals(2, repository.recent(Long.MAX_VALUE, 100).size());
        for (ReactionSample sample : samples) {
            repository.changeState(sample.getId(), sample.getRevision(), ReactionSampleState.ARCHIVED);
        }
        service.discover();
        assertTrue(repository.list(ReactionSampleState.OBSERVING, 0, 100).isEmpty());
    }

    @Test
    void unresolvedNewsSurvivesCacheExpiryAndRetriesWithoutUserInput() {
        supplyNews(List.of(news("示例公司业绩增长")));
        when(resolver.resolve(anyString())).thenReturn(List.of());
        service.discover();
        ReactionSample draft = repository.recent(Long.MAX_VALUE, 100).get(0);
        assertEquals(ReactionSampleState.DRAFT, draft.getState());
        assertNotNull(draft.getDiscoveryIssue());
        service.discover();
        verify(resolver, times(1)).resolve(anyString());
        supplyNews(List.of());
        when(resolver.resolve(anyString())).thenReturn(List.of(match("600519.SH", "示例公司")));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now.plusHours(7).atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai")));
        assertEquals(1, service.discover().getResolved());
        assertEquals(ReactionSampleState.OBSERVING, repository.recent(Long.MAX_VALUE, 100).get(0).getState());
    }

    @Test
    void missingTimeIsRetainedAndFutureOrUnrelatedNewsIsNotMadeIntoPriceSamples() {
        ResearchMaterial missing = news("示例公司年度业绩");
        missing.setPublishedAt(null);
        ResearchMaterial future = news("未来公司合同");
        future.setPublishedAt(now.plusDays(1));
        supplyNews(List.of(missing, future, news("市场午间概览")));
        service.discover();
        assertEquals(1, repository.recent(Long.MAX_VALUE, 100).size());
        assertEquals(ReactionSampleState.DRAFT, repository.recent(Long.MAX_VALUE, 100).get(0).getState());
        verifyNoInteractions(resolver);
    }

    @Test
    void archivedDuringEnrichmentCannotBeResurrected() {
        supplyNews(List.of(news("示例公司重大合同")));
        when(resolver.resolve(anyString())).thenAnswer(call -> {
            ReactionSample draft = repository.recent(Long.MAX_VALUE, 100).get(0);
            repository.changeState(draft.getId(), draft.getRevision(), ReactionSampleState.ARCHIVED);
            return List.of(match("600519.SH", "示例公司"));
        });
        assertEquals(0, service.discover().getResolved());
        assertEquals(1, repository.recent(Long.MAX_VALUE, 100).size());
        assertEquals(ReactionSampleState.ARCHIVED, repository.recent(Long.MAX_VALUE, 100).get(0).getState());
    }

    @Test
    void fillingPublicationTimeDoesNotChangeIdentityAfterPromotionOrArchival() {
        ResearchMaterial item = news("示例公司签订重大合同");
        item.setPublishedAt(null);
        supplyNews(List.of(item));
        service.discover();
        String identity = repository.recent(Long.MAX_VALUE, 100).get(0).getSourceIdentity();
        item.setPublishedAt(now.minusHours(1));
        service.discover();
        service.discover();
        var rows = repository.recent(Long.MAX_VALUE, 100);
        assertEquals(1, rows.size());
        assertEquals(identity, rows.get(0).getSourceIdentity());
        assertEquals(1, repository.sources(identity).size());
        repository.changeState(rows.get(0).getId(), rows.get(0).getRevision(), ReactionSampleState.ARCHIVED);
        service.discover();
        assertEquals(1, repository.recent(Long.MAX_VALUE, 100).size());
        assertEquals(ReactionSampleState.ARCHIVED, repository.recent(Long.MAX_VALUE, 100).get(0).getState());
    }

    private void verifyAutomaticPriceTracking(ReactionSample sample) {
        var registration = new ReactionRegistrationService();
        ReflectionTestUtils.setField(registration, "repository", repository);
        var refresh = new ReactionRefreshService();
        var bars = mock(com.finscope.rpc.quant.QuantDailyBarSource.class);
        var calendar = mock(com.finscope.rpc.quote.PythonTradingCalendarClient.class);
        List<LocalDate> dates = List.of("2026-09-11", "2026-09-14", "2026-09-15", "2026-09-16", "2026-09-17",
                "2026-09-18", "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24", "2026-09-25")
                .stream().map(LocalDate::parse).toList();
        var points = new java.util.ArrayList<com.finscope.domain.quant.data.QuantDailyBar>();
        for (int i = 0; i < dates.size(); i++) {
            var point = new com.finscope.domain.quant.data.QuantDailyBar();
            point.setTradeDate(dates.get(i));
            point.setClose(java.math.BigDecimal.valueOf(100 + i));
            point.setAdjustedClose(java.math.BigDecimal.valueOf(100 + i));
            point.setVolume(java.math.BigDecimal.valueOf(1000 + i));
            point.setAmount(java.math.BigDecimal.valueOf(100000 + i));
            points.add(point);
        }
        when(calendar.eventWindow(any())).thenReturn(dates);
        when(bars.fetch(anyString(), eq(1000))).thenReturn(new com.finscope.rpc.quant.QuantDailyBarBatch(
                points, "TEST", "TEST", "FRESH_PRIMARY", dates.get(10), List.of()));
        ReflectionTestUtils.setField(refresh, "repository", repository);
        ReflectionTestUtils.setField(refresh, "registration", registration);
        ReflectionTestUtils.setField(refresh, "dailyBars", bars);
        ReflectionTestUtils.setField(refresh, "calendar", calendar);
        ReflectionTestUtils.setField(refresh, "clock", Clock.fixed(Instant.parse("2026-09-25T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
        var updated = refresh.refresh(sample.getId());
        assertEquals(11, updated.getCalculation().getPoints().size());
        assertEquals(java.math.BigDecimal.valueOf(1000), updated.getCalculation().getPoints().get(0).getVolume());
        assertEquals(java.math.BigDecimal.valueOf(110), updated.getCalculation().getPoints().get(10).getClose());
        assertEquals(com.finscope.common.enums.investmentobservation.ReactionWindowStatus.READY,
                updated.getCalculation().getWindows().get(2).getStatus());
        // 后续归档使用后台更新后的版本号。
        sample.setRevision(updated.getRevision());
    }

    private ResearchMaterial news(String title) {
        ResearchMaterial item = new ResearchMaterial();
        item.setTitle(title);
        item.setContent("原始事实");
        item.setPublishedAt(now.minusHours(1));
        item.setProviderCode("TEST");
        item.setExternalId(title);
        item.setUrl("https://example.com/news");
        return item;
    }

    private ReactionStockMatch match(String code, String name) {
        ReactionStockMatch match = new ReactionStockMatch();
        match.setCode(code);
        match.setName(name);
        return match;
    }
    private void supplyNews(List<ResearchMaterial> values) {
        doAnswer(invocation -> {
            java.util.function.Consumer<List<com.finscope.domain.news.NewsReport>> consumer = invocation.getArgument(0);
            consumer.accept(values.stream().map(value -> {
                var report = new com.finscope.domain.news.NewsReport();
                report.setId(value.getProviderCode() + ":" + value.getExternalId());
                report.setTitle(value.getTitle());
                report.setContent(value.getContent());
                report.setUrl(value.getUrl());
                report.setPublishedAt(value.getPublishedAt());
                report.setFirstSeenAt(now);
                return report;
            }).toList());
            return null;
        }).when(materials).scan(any());
    }

}
