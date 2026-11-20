package com.finscope.service.majorevent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.enums.news.NewsInterpretationStatus;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.majorevent.MajorEventReadingRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.dao.news.NewsReportRepository;
import com.finscope.dao.news.NewsWindowSchemaMigrator;
import com.finscope.dao.radar.RadarEventInterpretationRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.news.NewsReportInterpretation;
import com.finscope.domain.news.NewsReportInterpretationView;
import com.finscope.domain.news.NewsReportVersion;
import com.finscope.domain.radar.RadarEventDetailSnapshot;
import com.finscope.domain.radar.RadarEventInterpretation;
import com.finscope.service.news.NewsReportInterpretationAgent;
import com.finscope.service.news.NewsReportInterpretationService;
import com.finscope.service.news.NewsWorkbenchCapabilities;
import com.finscope.service.radar.RadarEventInterpretationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MajorEventInterpretationServiceTest {
    @TempDir Path directory;
    private final MajorEventInterpretationService service = new MajorEventInterpretationService();
    private final NewsReportInterpretationAgent agent = mock(NewsReportInterpretationAgent.class);
    private final NewsReportInterpretationService news = mock(NewsReportInterpretationService.class);
    private final RadarEventInterpretationService radarInterpretations = mock(RadarEventInterpretationService.class);
    private final RadarRepository radar = mock(RadarRepository.class);
    private final RadarEventInterpretationRepository radarResults = mock(RadarEventInterpretationRepository.class);
    private final NewsWorkbenchCapabilities capabilities = mock(NewsWorkbenchCapabilities.class);
    private final MajorEventReadingRepository archive = new MajorEventReadingRepository();
    private final List<Runnable> jobs = new ArrayList<>();
    private MajorEventRepository events;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("archive.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(source);
        var schema = new DatabaseInitializer();
        ReflectionTestUtils.setField(schema, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(schema, "dataRoot", directory.toString());
        schema.afterPropertiesSet();
        var migration = new NewsWindowSchemaMigrator();
        ReflectionTestUtils.setField(migration, "jdbc", jdbc);
        ReflectionTestUtils.setField(migration, "transactionManager", new DataSourceTransactionManager(source));
        migration.afterPropertiesSet();
        var reports = new NewsReportRepository();
        ReflectionTestUtils.setField(reports, "jdbc", jdbc);
        events = new MajorEventRepository(jdbc);
        ReflectionTestUtils.setField(archive, "jdbc", jdbc);
        ReflectionTestUtils.setField(archive, "mapper", new ObjectMapper());
        var radarReadings = new MajorEventRadarReadings();
        ReflectionTestUtils.setField(radarReadings, "archive", archive);
        ReflectionTestUtils.setField(radarReadings, "interpretations", radarResults);
        ReflectionTestUtils.setField(service, "archive", archive);
        ReflectionTestUtils.setField(service, "events", events);
        ReflectionTestUtils.setField(service, "reports", reports);
        ReflectionTestUtils.setField(service, "news", news);
        ReflectionTestUtils.setField(service, "radar", radar);
        ReflectionTestUtils.setField(service, "radarReadings", radarReadings);
        ReflectionTestUtils.setField(service, "radarInterpretations", radarInterpretations);
        ReflectionTestUtils.setField(service, "capabilities", capabilities);
        ReflectionTestUtils.setField(service, "agent", agent);
        ReflectionTestUtils.setField(service, "agentRuns", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "executor", (Executor) jobs::add);
        when(capabilities.isInterpretationEnabled()).thenReturn(true);
    }

    @Test
    void readsAndGeneratesAgainstTheExactArchivedReportVersion() {
        var event = save("NEWS_ITEM", "CLS:1");
        jdbc.update("INSERT INTO news_report_version(report_id,version,title,content,detected_at) VALUES(?,?,?,?,?)",
                "CLS:1", 1, event.getTitle(), event.getSummary() + "\n", event.getCreatedAt().minusDays(1).toString());
        jdbc.update("INSERT INTO news_report_version(report_id,version,title,content,detected_at) VALUES(?,?,?,?,?)",
                "CLS:1", 2, event.getTitle(), "后续修订内容", event.getCreatedAt().plusDays(1).toString());
        var original = new NewsReportInterpretationView();
        when(news.get("CLS:1", 1)).thenReturn(original);
        when(news.request("CLS:1", 1)).thenReturn(original);
        assertSame(original, service.reading(event.getId(), false));
        verify(news, never()).request(anyString(), anyInt());
        assertSame(original, service.reading(event.getId(), true));
        assertTrue(jobs.isEmpty());
    }

    @Test
    void reusesARadarReadingAfterRedisExpiryAndWithTheModelDisabled() {
        var event = save("RADAR_EVENT", "12345");
        when(radar.archivedEventId("12345")).thenReturn(12345L);
        var stored = new RadarEventInterpretation();
        var result = new RadarEventInterpretation.Result();
        result.setFactSummary("初始事实");
        result.setWhyItMatters("现有雷达解读");
        result.setImpactChain(List.of("订单传到供应商"));
        stored.setResult(result);
        stored.setStatus("SUCCESS");
        when(radarResults.latestSuccess(12345L)).thenReturn(Optional.of(stored));
        when(capabilities.isInterpretationEnabled()).thenReturn(false);
        var view = service.reading(event.getId(), false);
        assertFalse(view.isAvailable());
        assertTrue(view.getCurrent().getResult().getWhyItMatters().contains("现有雷达解读"));
        reset(radar, radarResults);
        assertEquals(view.getCurrent().getResult(), service.reading(event.getId(), false).getCurrent().getResult());
        verifyNoInteractions(radar, radarResults, agent);
    }

    @Test
    void sharesTheLiveRadarJobAndOnlySubmitsItOnAnExplicitRequest() {
        var event = save("RADAR_EVENT", "央行:预期");
        when(radar.archivedEventId(event.getOriginKey())).thenReturn(42L);
        var snapshot = RadarEventDetailSnapshot.builder().build();
        when(radar.findDetailSnapshot(42L)).thenReturn(Optional.of(snapshot));
        assertNull(service.reading(event.getId(), false).getCurrent());
        verify(radarInterpretations, never()).request(anyLong());
        var pending = new RadarEventInterpretation();
        pending.setStatus("QUEUED");
        when(radarInterpretations.request(42L)).thenReturn(pending);
        assertEquals(NewsInterpretationStatus.QUEUED, service.reading(event.getId(), true).getCurrent().getStatus());
        when(radarInterpretations.current(snapshot)).thenReturn(Optional.of(pending));
        assertEquals(NewsInterpretationStatus.QUEUED, service.reading(event.getId(), false).getCurrent().getStatus());
        verify(radarInterpretations).request(42L);
        assertTrue(jobs.isEmpty());
    }

    @Test
    void generatesOnlyOnDemandFromTheSnapshotAndSharesOneRetry() throws Exception {
        var event = save("NEWS_ITEM", "legacy:1");
        var result = new NewsReportInterpretation.Result();
        result.setFacts(List.of("归档解读"));
        when(agent.interpret(any(), any())).thenThrow(new IllegalStateException("failed")).thenReturn(result);
        long id = event.getId();
        assertNull(service.reading(id, false).getCurrent());
        assertTrue(jobs.isEmpty());
        service.reading(id, true);
        assertEquals(NewsInterpretationStatus.RUNNING, service.reading(id, true).getCurrent().getStatus());
        assertEquals(1, jobs.size());
        jobs.removeFirst().run();
        assertEquals(NewsInterpretationStatus.FAILED, service.reading(id, false).getCurrent().getStatus());
        service.reading(id, true);
        jobs.removeFirst().run();
        assertEquals(result, service.reading(id, true).getCurrent().getResult());
        var input = ArgumentCaptor.forClass(NewsReportVersion.class);
        verify(agent, times(2)).interpret(any(), input.capture());
        assertEquals(event.getSummary(), input.getValue().getContent());
        assertTrue(jobs.isEmpty());
    }

    @Test
    void aSourceCompletionWinsAgainstAnExpiredArchiveWorkerAndIsRemovedWithTheEvent() {
        var event = save("RADAR_EVENT", "legacy-key");
        long id = event.getId();
        assertTrue(archive.claim(id, "old", 0));
        assertTrue(archive.claim(id, "new", 180_000));
        assertFalse(archive.start(id, "old", 180_001));
        var source = new NewsReportInterpretation.Result();
        source.setFacts(List.of("源解读"));
        archive.retainRadar("legacy-key", 12345L, source);
        archive.finish(id, "new", null);
        assertEquals(source, archive.find(id, 180_002).orElseThrow().getResult());
        events.deleteById(id);
        assertTrue(archive.find(id, 180_003).isEmpty());
    }

    private MajorEvent save(String type, String key) {
        var event = new MajorEvent();
        event.setOriginType(type);
        event.setOriginKey(key);
        event.setTitle("新闻标题");
        event.setSummary("当时归档的正文");
        event.setOccurredDate(LocalDate.now());
        return events.save(event);
    }
}
