package com.finscope.service.investmentobservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.dao.investmentobservation.ReactionSchemaMigrator;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.domain.investmentobservation.ReactionRegistration;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import com.finscope.rpc.quote.PythonTradingCalendarClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReactionWorkflowIntegrationTest {
    @TempDir
    Path temp;

    @Test
    void realMajorEventToPersistedReactionKeepsIdentitySnapshotAndRejectsDuplicateConfirmation() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temp.resolve("reaction.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE major_event(id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type TEXT,origin_key TEXT,"
                + "title TEXT,summary TEXT,source_name TEXT,source_url TEXT,category_code TEXT,occurred_date TEXT,"
                + "note TEXT,created_at TEXT,updated_at TEXT)");
        ReactionSchemaMigrator migration = new ReactionSchemaMigrator();
        ReflectionTestUtils.setField(migration, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(migration, "transactionManager", new DataSourceTransactionManager(dataSource));
        migration.afterPropertiesSet();
        ReactionSampleRepository repository = new ReactionSampleRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(repository, "objectMapper", new ObjectMapper().registerModule(new JavaTimeModule()));
        MajorEventRepository majors = new MajorEventRepository(jdbc);
        MajorEvent source = new MajorEvent();
        source.setTitle("上市公司签订重大合同");
        source.setSummary("合同事实");
        source.setOriginType("NEWS_ITEM");
        source.setOriginKey("origin-900");
        source.setSourceUrl("https://example.com/original-contract");
        source.setOccurredDate(LocalDate.parse("2026-09-18"));
        majors.save(source);
        ReactionRegistrationService registration = new ReactionRegistrationService();
        ReflectionTestUtils.setField(registration, "repository", repository);
        ReflectionTestUtils.setField(registration, "majorEvents", majors);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ReflectionTestUtils.setField(registration, "clock", clock);
        assertEquals(source.getId(), registration.candidates().get(0).getMajorEventId());
        ReactionSample draft = registration.createDraft(source.getId());
        assertEquals(draft.getId(), registration.createDraft(source.getId()).getId());
        ReactionRegistration command = new ReactionRegistration();
        command.setInstrumentCode("600519.SH");
        command.setInstrumentName("示例公司");
        command.setEventType(ReactionEventType.CONTRACT);
        command.setRelationNote("公司为正式合同签署方");
        command.setPublishedAt(LocalDateTime.parse("2026-09-18T09:00:00"));
        ReactionSample confirmed = registration.confirm(draft.getId(), command);
        assertTrue(confirmed.isHistoricalBackfill());
        jdbc.update("UPDATE major_event SET title='changed' WHERE id=?", source.getId());
        assertEquals("上市公司签订重大合同", registration.require(confirmed.getId()).getTitle());

        List<LocalDate> sessions = List.of("2026-09-10", "2026-09-11", "2026-09-14", "2026-09-15", "2026-09-16",
                "2026-09-17", "2026-09-18", "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24")
                .stream().map(LocalDate::parse).toList();
        List<QuantDailyBar> points = new ArrayList<>();
        for (LocalDate date : sessions) {
            QuantDailyBar point = new QuantDailyBar();
            point.setTradeDate(date);
            point.setAdjustedClose(BigDecimal.TEN);
            point.setVolume(BigDecimal.ONE);
            points.add(point);
        }
        QuantDailyBarSource bars = mock(QuantDailyBarSource.class);
        when(bars.fetch(anyString(), eq(1000))).thenReturn(new QuantDailyBarBatch(points, "TEST", "TEST", "FRESH_PRIMARY", sessions.get(10), List.of()));
        PythonTradingCalendarClient calendar = mock(PythonTradingCalendarClient.class);
        when(calendar.eventWindow(any())).thenReturn(sessions);
        ReactionRefreshService refresh = new ReactionRefreshService();
        ReflectionTestUtils.setField(refresh, "repository", repository);
        ReflectionTestUtils.setField(refresh, "registration", registration);
        ReflectionTestUtils.setField(refresh, "dailyBars", bars);
        ReflectionTestUtils.setField(refresh, "calendar", calendar);
        ReflectionTestUtils.setField(refresh, "clock", clock);
        ReactionSample result = refresh.refresh(confirmed.getId());
        assertEquals(ReactionWindowStatus.READY, result.getCalculation().getWindows().get(2).getStatus());
        assertEquals("NEWS_ITEM", result.getSourceOriginType());
        assertEquals("origin-900", result.getSourceOriginKey());
        assertEquals(source.getSourceUrl(), result.getSourceUrl());
        assertTrue(repository.findDue(LocalDateTime.parse("2026-09-30T00:00:00"), 20).isEmpty());
        ReactionSample duplicate = registration.createDraft(source.getId());
        assertEquals(ErrorCode.DUPLICATE_OPERATION,
                assertThrows(BusinessException.class, () -> registration.confirm(duplicate.getId(), command)).getErrorCode());
        registration.archive(result.getId(), result.getRevision(), true);
        ReactionSample archived = registration.require(result.getId());
        registration.archive(archived.getId(), archived.getRevision(), false);
        assertEquals(result.getCalculation(), registration.require(result.getId()).getCalculation());
    }
}
