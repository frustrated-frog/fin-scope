package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarEventSignal;
import com.finscope.domain.radar.RadarSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarRepositoryTest {
    @TempDir Path tempDir;
    private RadarRepository repository;
    private JdbcTemplate jdbc;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 31, 15, 0);

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("radar.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        repository = new RadarRepository(jdbc);
    }

    @Test
    void captureIsIdempotentAndCompletesClassificationWithoutChangingFirstSeen() {
        RadarSignal first = repository.capture(signal("CLS:1", null), now);
        RadarSignal updated = repository.capture(signal("CLS:1", "COMPANY"), now.plusMinutes(2));

        List<RadarSignal> active = repository.findActiveSignals(now.minusHours(48), 500);
        assertEquals(1, active.size());
        assertEquals(first.getId(), updated.getId());
        assertEquals("COMPANY", active.get(0).getCategoryCode());
        assertEquals(now, active.get(0).getFirstSeenAt());
        assertEquals(now.plusMinutes(2), active.get(0).getLastSeenAt());
    }

    @Test
    void eventAndSignalLinksRoundTripAndSupportRankedFiltering() {
        RadarSignal signal = repository.capture(signal("THS:9", "COMPANY"), now);
        RadarEvent event = repository.saveEvent(event("COMPANY:宁德时代:发布:电池"));
        RadarEventSignal link = new RadarEventSignal();
        link.setEventId(event.getId());
        link.setSignalId(signal.getId());
        link.setRelationType("PRIMARY");
        link.setMatchScore(0.91);
        link.setMatchReason("主体与动作一致");
        repository.replaceEventSignals(event.getId(), Collections.singletonList(link));

        assertNotNull(event.getId());
        assertEquals("宁德时代发布新一代电池", repository.findEvent(event.getId()).get().getCanonicalTitle());
        assertEquals(1, repository.findSignalsByEventId(event.getId()).size());
        assertEquals(1, repository.findRanked("COMPANY", true, 20).size());
        assertEquals("主体与动作一致", repository.findEventSignals(event.getId()).get(0).getMatchReason());
    }

    @Test
    void rankedEventsFollowResearchPriorityBeforeHotspotTieBreak() {
        RadarEvent highHotspot = event("event:hotspot-first");
        highHotspot.setHotspotScore(95);
        highHotspot.setPriorityScore(40);
        RadarEvent highPriority = event("event:priority-first");
        highPriority.setHotspotScore(80);
        highPriority.setPriorityScore(90);

        repository.saveEvent(highHotspot);
        repository.saveEvent(highPriority);

        List<RadarEvent> ranked = repository.findRanked("ALL", false, 20);

        assertEquals(highPriority.getEventKey(), ranked.get(0).getEventKey());
        assertEquals(highHotspot.getEventKey(), ranked.get(1).getEventKey());
    }

    @Test
    void rankedRadarHidesLegacyCategoryDuplicatesWithTheSameTitle() {
        RadarEvent oldCategory = event("UNCLASSIFIED:港股人工智能股走弱兆易创新跌超5:事件:信息");
        oldCategory.setCanonicalTitle("港股人工智能股走弱 兆易创新跌超5%");
        oldCategory.setPriorityScore(78);
        oldCategory.setHotspotScore(56);
        RadarEvent newCategory = event("MARKET_MOVE:港股人工智能股走弱兆易创新跌超5:事件:信息");
        newCategory.setCanonicalTitle("港股人工智能股走弱 兆易创新跌超5%");
        newCategory.setPriorityScore(83);
        newCategory.setHotspotScore(43);
        repository.saveEvent(oldCategory);
        RadarEvent savedNew = repository.saveEvent(newCategory);

        List<RadarEvent> ranked = repository.findRanked("ALL", false, 20);

        assertEquals(1, ranked.size());
        assertEquals(savedNew.getId(), ranked.get(0).getId());
    }

    @Test
    void dashboardRankingReturnsOnlyTheFiveHottestEventsInItsCategory() {
        for (int index = 1; index <= 6; index++) {
            RadarEvent event = event("event:finance:" + index);
            event.setDashboardCategory("FINANCE");
            event.setHotspotScore(index * 10);
            event.setHotspotLifecycleState(index == 6 ? "RISING" : "STABLE");
            repository.saveEvent(event);
        }
        RadarEvent technology = event("event:technology");
        technology.setDashboardCategory("TECHNOLOGY");
        technology.setHotspotScore(100);
        repository.saveEvent(technology);
        RadarEvent quiet = event("event:finance:quiet");
        quiet.setDashboardCategory("FINANCE");
        quiet.setStatus("QUIET");
        quiet.setHotspotScore(65);
        repository.saveEvent(quiet);
        RadarEvent expired = event("event:finance:expired");
        expired.setDashboardCategory("FINANCE");
        expired.setStatus("EXPIRED");
        expired.setHotspotScore(100);
        repository.saveEvent(expired);

        List<RadarEvent> ranked = repository.findTopByDashboardCategory("FINANCE", 5);

        assertEquals(5, ranked.size());
        assertEquals("event:finance:quiet", ranked.get(0).getEventKey());
        assertEquals("QUIET", ranked.get(0).getStatus());
        assertEquals("FINANCE", ranked.get(0).getDashboardCategory());
        assertEquals("event:finance:3", ranked.get(4).getEventKey());
    }

    @Test
    void rankedQueryStillFillsTheRequestedLimitAfterLegacyDuplicates() {
        for (int index = 0; index < 501; index++) {
            RadarEvent duplicate = event("COMPANY:legacy:" + index);
            duplicate.setCanonicalTitle("旧分类重复标题");
            duplicate.setPriorityScore(1_000 - index);
            repository.saveEvent(duplicate);
        }
        RadarEvent distinct = event("event:distinct");
        distinct.setCanonicalTitle("应进入榜单的独立事件");
        distinct.setPriorityScore(1);
        repository.saveEvent(distinct);

        List<RadarEvent> ranked = repository.findRanked("ALL", false, 2);

        assertEquals(2, ranked.size());
        assertEquals("event:distinct", ranked.get(1).getEventKey());
    }

    @Test
    void findsAndBackfillsEventsMissingDashboardClassification() {
        RadarEvent event = event("event:unclassified");
        event.setDashboardCategory("UNCLASSIFIED");
        RadarEvent saved = repository.saveEvent(event);

        assertEquals(1, repository.findEventsMissingDashboardCategory(500).size());

        repository.updateDashboardCategory(saved.getId(), "TECHNOLOGY");

        assertTrue(repository.findEventsMissingDashboardCategory(500).isEmpty());
        assertEquals("TECHNOLOGY", repository.findEvent(saved.getId()).get().getDashboardCategory());
    }

    @Test
    void oldSignalsAreExpiredAndExcludedFromActiveWindow() {
        repository.capture(signal("OLD:1", "GLOBAL"), now.minusDays(15));
        repository.expireSignals(now.minusDays(14), now);

        assertTrue(repository.findActiveSignals(now.minusHours(48), 500).isEmpty());
        assertEquals("EXPIRED", jdbc.queryForObject(
                "SELECT status FROM radar_signal WHERE item_id='OLD:1'", String.class));
    }

    @Test
    void staleBackgroundEnhancementCannotOverwriteANewerRefresh() {
        RadarEvent stale = repository.saveEvent(event("event:stale"));
        RadarEvent refreshed = event("event:stale");
        refreshed.setCanonicalTitle("新一轮规则标题");
        refreshed.setUpdatedAt(now.plusMinutes(1));
        repository.saveEvent(refreshed);

        stale.setCanonicalTitle("旧后台Agent标题");
        stale.setEvidenceStatus("SUCCESS");
        repository.updateEvidenceEnhancement(stale);

        RadarEvent actual = repository.findEvent(stale.getId()).get();
        assertEquals("新一轮规则标题", actual.getCanonicalTitle());
        assertEquals(null, actual.getEvidenceStatus());
    }

    @Test
    void eventsMissingFromTheLatestGenerationAreExpiredOnceOutsideTheWindow() {
        RadarEvent retained = repository.saveEvent(event("event:retained"));
        RadarEvent obsolete = repository.saveEvent(event("event:obsolete"));

        repository.expireEventsExcept(new HashSet<String>(Collections.singletonList("event:retained")),
                now.plusMinutes(1), now.plusMinutes(2));

        assertEquals("ACTIVE", repository.findEvent(retained.getId()).get().getStatus());
        assertEquals("EXPIRED", repository.findEvent(obsolete.getId()).get().getStatus());
    }

    @Test
    void eventsInsideWindowWithoutFreshSignalsBecomeQuietNotExpired() {
        RadarEvent insideWindow = repository.saveEvent(event("event:inside-window"));
        RadarEvent outsideWindow = repository.saveEvent(event("event:outside-window"));
        outsideWindow.setLastSeenAt(now.minusDays(3));
        repository.saveEvent(outsideWindow);

        repository.expireEventsExcept(Collections.singleton("event:other"), now.minusHours(2), now.plusMinutes(1));

        assertEquals("QUIET", repository.findEvent(insideWindow.getId()).get().getStatus());
        assertEquals("EXPIRED", repository.findEvent(outsideWindow.getId()).get().getStatus());
    }

    @Test
    void recentEventQueryExcludesExpiredEvents() {
        repository.saveEvent(event("event:active"));
        RadarEvent expired = event("event:expired");
        expired.setStatus("EXPIRED");
        repository.saveEvent(expired);

        List<RadarEvent> recent = repository.findEventsSince(now.minusDays(30), 20);

        assertEquals(1, recent.size());
        assertEquals("event:active", recent.get(0).getEventKey());
    }

    private RadarSignal signal(String itemId, String category) {
        RadarSignal signal = new RadarSignal();
        signal.setItemId(itemId);
        signal.setProviderCode(itemId.split(":")[0]);
        signal.setSourceName("公开资讯");
        signal.setSourceTier("TIER_1");
        signal.setCategoryCode(category);
        signal.setTitle("宁德时代发布新一代电池");
        signal.setContent("公司公布新产品和量产计划");
        signal.setUrl("https://example.com/" + itemId);
        signal.setPublishedAt(now.minusMinutes(10));
        signal.setContentHash("hash-" + itemId);
        signal.setStatus("ACTIVE");
        return signal;
    }

    private RadarEvent event(String eventKey) {
        RadarEvent event = new RadarEvent();
        event.setEventKey(eventKey);
        event.setCanonicalTitle("宁德时代发布新一代电池");
        event.setSummary("公司公布新产品和量产计划");
        event.setCategoryCode("COMPANY");
        event.setStatus("ACTIVE");
        event.setFirstSeenAt(now.minusMinutes(10));
        event.setLastSeenAt(now);
        event.setSourceCount(2);
        event.setSignalCount(2);
        event.setPriorityScore(82);
        event.setScoreExplanation("{\"novelty\":25}");
        event.setWatchlistRelevance(25);
        event.setWatchlistExplanation("宁德时代");
        event.setUncertainty("等待公司公告");
        event.setNextObservation("核对量产时间");
        event.setUpdatedAt(now);
        return event;
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE radar_signal(id INTEGER PRIMARY KEY AUTOINCREMENT,item_id TEXT NOT NULL UNIQUE,"
                + "provider_code TEXT,source_name TEXT,source_tier TEXT,category_code TEXT,title TEXT NOT NULL,"
                + "content TEXT,url TEXT,published_at TEXT,first_seen_at TEXT NOT NULL,last_seen_at TEXT NOT NULL,"
                + "content_hash TEXT NOT NULL,status TEXT NOT NULL,source_rank INTEGER,previous_source_rank INTEGER,"
                + "source_weight REAL NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE radar_event(id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE,"
                + "canonical_title TEXT NOT NULL,summary TEXT,category_code TEXT,dashboard_category TEXT NOT NULL DEFAULT 'UNCLASSIFIED',status TEXT NOT NULL,"
                + "first_seen_at TEXT NOT NULL,last_seen_at TEXT NOT NULL,source_count INTEGER NOT NULL DEFAULT 0,"
                + "signal_count INTEGER NOT NULL DEFAULT 0,hotspot_score INTEGER NOT NULL DEFAULT 0,hotspot_explanation TEXT,hotspot_lifecycle_state TEXT,"
                + "priority_score INTEGER NOT NULL DEFAULT 0,"
                + "score_explanation TEXT,watchlist_relevance INTEGER NOT NULL DEFAULT 0,watchlist_explanation TEXT,"
                + "uncertainty TEXT,next_observation TEXT,evidence_status TEXT,evidence_summary TEXT,evidence_warning TEXT,"
                + "evidence_fingerprint TEXT,evidence_count INTEGER NOT NULL DEFAULT 0,"
                + "evidence_source_count INTEGER NOT NULL DEFAULT 0,evidence_updated_at TEXT,updated_at TEXT NOT NULL)");
        jdbc.execute("CREATE TABLE radar_event_signal(event_id INTEGER NOT NULL,signal_id INTEGER NOT NULL,"
                + "relation_type TEXT NOT NULL,match_score REAL NOT NULL,match_reason TEXT,"
                + "PRIMARY KEY(event_id,signal_id),FOREIGN KEY(event_id) REFERENCES radar_event(id) ON DELETE CASCADE,"
                + "FOREIGN KEY(signal_id) REFERENCES radar_signal(id) ON DELETE CASCADE)");
    }
}
