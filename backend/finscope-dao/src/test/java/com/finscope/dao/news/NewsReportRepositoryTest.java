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
    void migrationQueuesRecentCurrentVersionsOnceAndKeepsCompletedVersionsAcknowledged() {
        var recent = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusMinutes(5);
        var report = report("legacy", "旧库报道");
        report.setPublishedAt(recent);
        report.setFirstSeenAt(recent);
        report.setLastSeenAt(recent);
        repository.ingest(List.of(report));
        report.setContent("修订后的正文");
        repository.ingest(List.of(report));
        jdbc.execute("DROP INDEX idx_news_version_due");
        jdbc.execute("ALTER TABLE news_report_version DROP COLUMN ai_due_at");
        var migration = new NewsWindowSchemaMigrator();
        ReflectionTestUtils.setField(migration, "jdbc", jdbc);
        ReflectionTestUtils.setField(migration, "transactionManager", new DataSourceTransactionManager(jdbc.getDataSource()));
        migration.afterPropertiesSet();
        assertEquals(List.of(2), repository.pendingAi(recent, recent.plusMinutes(1), 10).stream()
                .map(NewsReport::getContentVersion).toList());
        jdbc.update("UPDATE news_report_version SET ai_due_at=NULL WHERE version=2");
        migration.afterPropertiesSet();
        assertTrue(repository.pendingAi(recent, recent.plusMinutes(1), 10).isEmpty());
    }

    @Test
    void recordsCommittedVersionChangesBySourceEvenAcrossChunks() {
        var status = com.finscope.common.enums.news.NewsSourceStatus.HEALTHY;
        repository.beginFetch("run", now, List.of(new NewsFetchCount("CLS", status, 3, 0)));
        var first = report("1", "公司公告");
        assertTrue(repository.ingestFetchBatch(List.of(first), "run"));
        assertFalse(repository.ingestFetchBatch(List.of(first), "run"));
        first.setContent("公告更正");
        assertTrue(repository.ingestFetchBatch(List.of(first), "run"));
        repository.finishFetch("run", now.plusSeconds(1));
        assertEquals(Map.of("added", 1, "revised", 1, "unchanged", 1), jdbc.queryForMap(
                "SELECT added,revised,unchanged FROM news_fetch_batch WHERE batch_id='run' AND completed_at IS NOT NULL"));
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

    @Test
    void aiClassificationSurvivesUnchangedFetchButNeverOverwritesANewerOrManualVersion() {
        var original = report("a", "公司合同");
        repository.ingest(List.of(original));
        var analysis = new com.finscope.domain.news.ai.NewsAiAnalysis();
        analysis.setCategoryCode("COMPANY");
        var categories = List.of(new NewsCategory("COMPANY", "公司", "经营事项", true, 1));
        assertTrue(repository.applyAi("a", original.getTitle(), original.getContent(), analysis, categories, "news-understanding-v1"));
        original.setCategoryCode("INDUSTRY");
        repository.ingest(List.of(original));
        assertEquals("COMPANY", repository.find("a").orElseThrow().getCategoryCode());
        original.setContent("修订正文");
        repository.ingest(List.of(original));
        assertFalse(repository.applyAi("a", original.getTitle(), "原文", analysis, categories, "news-understanding-v1"));
        assertEquals("INDUSTRY", repository.find("a").orElseThrow().getCategoryCode());
        repository.review("a", "MANUAL", "人工", "人工判断");
        assertFalse(repository.applyAi("a", original.getTitle(), original.getContent(), analysis, categories, "news-understanding-v1"));
        assertEquals("MANUAL", repository.find("a").orElseThrow().getCategoryCode());
    }

    @Test
    void eventGroupingKeepsOriginalsFiltersAndArrivalWatermarks() {
        var one = report("a", "合同公告");
        one.setCategoryCode("COMPANY");
        var two = report("b", "转述同一合同");
        two.setSourceName("同花顺");
        two.setCategoryCode("COMPANY");
        var three = report("c", "另一公司合同");
        three.setCategoryCode("INDUSTRY");
        repository.ingest(List.of(one, two, three));
        link("a", "e1", "NEW_EVENT");
        link("b", "e1", "DUPLICATE");
        link("c", "e2", "NEW_EVENT");
        var query = query();
        query.setSize(1);
        var page = repository.query(query, now.minusHours(1), now);
        assertEquals(2, page.getTotal());
        assertEquals(2, page.getCategoryCounts().get("ALL"));
        query.setPage(1);
        var grouped = repository.query(query, now.minusHours(1), now).getItems().get(0);
        assertEquals("a", grouped.getId());
        assertEquals(2, grouped.getMergedReportCount());
        assertEquals(List.of("b"), repository.related("a").stream().map(NewsReport::getId).toList());
        query.setSource("同花顺");
        query.setPage(0);
        assertEquals("b", repository.query(query, now.minusHours(1), now).getItems().get(0).getId());
        query.setSource("ALL");
        query.setGroupEvents(false);
        assertEquals(3, repository.count(query, now.minusHours(1), now));
        query.setGroupEvents(true);
        repository.ingest(List.of(report("late", "延迟新报道")));
        link("late", "e3", "NEW_EVENT");
        assertEquals(2, repository.count(query, now.minusHours(1), now));
        two.setContent("正文已被修正，不再是相同报道");
        repository.ingest(List.of(two));
        assertEquals(3, repository.count(query, now.minusHours(1), now));
        assertTrue(repository.related("a").isEmpty());
    }

    private void link(String id, String event, String relation) {
        jdbc.update("INSERT INTO news_ai_report VALUES(?,?,?,?,1)", id, "hash" + id, event, relation);
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
