package com.finscope.dao.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.news.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NewsReportRepositoryTest {
    @TempDir Path temp;
    private NewsReportRepository repository;
    private JdbcTemplate jdbc;
    private final LocalDateTime now = LocalDateTime.parse("2026-09-21T10:00:00");

    @BeforeEach
    void setup() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + temp.resolve("news.db"));
        jdbc = new JdbcTemplate(source);
        var migration = new NewsWindowSchemaMigrator();
        ReflectionTestUtils.setField(migration, "jdbc", jdbc);
        ReflectionTestUtils.setField(migration, "transactionManager", new DataSourceTransactionManager(source));
        migration.afterPropertiesSet();
        migration.afterPropertiesSet();
        repository = new NewsReportRepository();
        ReflectionTestUtils.setField(repository, "jdbc", jdbc);
        ReflectionTestUtils.setField(repository, "mapper", new ObjectMapper());
        jdbc.execute("CREATE TABLE investment_reaction_source(origin_type TEXT,origin_key TEXT,event_key TEXT)");
        jdbc.execute("CREATE TABLE major_event(origin_type TEXT,origin_key TEXT)");
    }

    @Test
    void successiveSourceSnapshotsAccumulateAndSearchBeyondFirstHundred() {
        List<NewsReport> batch = new ArrayList<>();
        for (int i = 1; i <= 250; i++) {
            batch.add(report("CLS:" + i, "日常新闻" + i));
        }
        repository.ingest(batch);
        repository.ingest(List.of(report("CLS:251", "目标公司中标")));
        NewsWindowQuery query = query();
        query.setQuery("目标公司");
        NewsWindowPage page = repository.query(query, now.minusHours(36), now);
        assertEquals(1, page.getTotal());
        assertEquals("CLS:251", page.getItems().get(0).getId());
        assertEquals(251, repository.query(query(), now.minusHours(36), now).getTotal());
        assertEquals(250, repository.scan(now.minusHours(36), now, 0, 250).size());
        assertEquals(1, repository.scan(now.minusHours(36), now, 250, 250).size());
    }

    @Test
    void onlyContentChangesCreateVersionAndOldReadRequestCannotHideNewVersion() {
        NewsReport first = report("CLS:1", "公司中标");
        repository.ingest(List.of(first));
        assertTrue(repository.markRead(first.getId(), 1));
        first.setLastSeenAt(now.plusMinutes(1));
        first.setCategoryCode("INDUSTRY");
        repository.ingest(List.of(first));
        assertEquals(1, repository.versions(first.getId()).size());
        assertFalse(repository.find(first.getId()).orElseThrow().isUnread());
        first.setContent("金额由待披露更新为334万元");
        repository.ingest(List.of(first));
        assertTrue(repository.markRead(first.getId(), 1));
        assertFalse(repository.markRead(first.getId(), 99));
        assertTrue(repository.find(first.getId()).orElseThrow().isUnread());
        assertEquals(2, repository.versions(first.getId()).size());
        assertEquals("原文", repository.versions(first.getId()).get(1).getContent());
        assertEquals(1, repository.find(first.getId()).orElseThrow().getArrivalSequence());
    }

    @Test
    void manualCategorySurvivesReingestAndUnclassifiedCanBeReviewed() {
        NewsReport report = report("CLS:1", "公司公告");
        repository.ingest(List.of(report));
        assertTrue(repository.review(report.getId(), "COMPANY", "公司", "标题中的主体"));
        report.setCategoryCode("INDUSTRY");
        repository.ingest(List.of(report));
        assertEquals("COMPANY", repository.find(report.getId()).orElseThrow().getCategoryCode());
        assertEquals(1, repository.versions(report.getId()).size());
    }

    @Test
    void stableArrivalWatermarkPreventsLateArrivalFromShiftingPages() {
        repository.ingest(List.of(report("CLS:1", "一"), report("CLS:2", "二"), report("CLS:3", "三")));
        NewsWindowQuery query = query();
        query.setSize(2);
        var first = repository.query(query, now.minusHours(36), now);
        repository.ingest(List.of(report("CLS:4", "补录")));
        query.setPage(1);
        var second = repository.query(query, now.minusHours(36), now);
        assertEquals(List.of("CLS:3", "CLS:2"), first.getItems().stream().map(NewsReport::getId).toList());
        assertEquals(List.of("CLS:1"), second.getItems().stream().map(NewsReport::getId).toList());
    }

    @Test
    void literalSearchAndUnreadCountsAreAppliedBeforePagination() {
        repository.ingest(List.of(report("1", "公司100%增长"), report("2", "公司100亿收入")));
        NewsWindowQuery query = query();
        query.setQuery("100%");
        assertEquals(1, repository.query(query, now.minusHours(36), now).getTotal());
        repository.markRead("1", 1);
        query.setQuery("");
        query.setUnreadOnly(true);
        assertEquals("2", repository.query(query, now.minusHours(36), now).getItems().get(0).getId());
        query.setExclude("收入");
        assertEquals(0, repository.query(query, now.minusHours(36), now).getTotal());
    }

    @Test
    void retentionKeepsInvestmentAndJournalEvidenceAndCleansOrphans() {
        repository.ingest(List.of(report("1", "观察"), report("2", "普通"), report("3", "大事记")));
        jdbc.update("INSERT INTO investment_reaction_source VALUES('NEWS_ITEM','1','EVENT:1')");
        jdbc.update("INSERT INTO major_event VALUES('NEWS_ITEM','3')");
        repository.prune(now.plusDays(8));
        assertTrue(repository.find("1").isPresent());
        assertTrue(repository.find("3").isPresent());
        assertTrue(repository.find("2").isEmpty());
        assertTrue(repository.versions("2").isEmpty());
    }

    @Test
    void savedFiltersRoundTripAndDeletionIsIdempotent() {
        NewsSavedFilter filter = new NewsSavedFilter();
        filter.setId("f1");
        filter.setName("芯片订单");
        filter.setQuery(query());
        filter.getQuery().setExclude("减持");
        repository.saveFilter(filter);
        assertEquals("减持", repository.filters().get(0).getQuery().getExclude());
        assertTrue(repository.deleteFilter("f1"));
        assertFalse(repository.deleteFilter("f1"));
    }

    @Test
    void relatedReportsUseThePersistedEventAssociation() {
        repository.ingest(List.of(report("1", "合同公告"), report("2", "另一来源合同公告"), report("3", "无关合同")));
        jdbc.update("INSERT INTO investment_reaction_source VALUES('NEWS_ITEM','1','EVENT:1')");
        jdbc.update("INSERT INTO investment_reaction_source VALUES('NEWS_ITEM','2','EVENT:1')");
        jdbc.update("INSERT INTO investment_reaction_source VALUES('NEWS_ITEM','3','EVENT:3')");
        assertEquals(List.of("2"), repository.related("1").stream().map(NewsReport::getId).toList());
    }

    private NewsWindowQuery query() {
        NewsWindowQuery query = new NewsWindowQuery();
        query.setAsOfSequence(repository.latestSequence());
        return query;
    }

    private NewsReport report(String id, String title) {
        NewsReport value = new NewsReport();
        value.setId(id);
        value.setTitle(title);
        value.setContent("原文");
        value.setProviderCode("CLS");
        value.setSourceName("财联社");
        value.setKind("FLASH");
        value.setPublishedAt(now.minusMinutes(1));
        value.setFirstSeenAt(now);
        value.setLastSeenAt(now);
        return value;
    }
}
