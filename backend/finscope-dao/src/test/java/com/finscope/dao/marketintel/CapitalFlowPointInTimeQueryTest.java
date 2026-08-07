package com.finscope.dao.marketintel;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalFlowPointInTimeQueryTest {
    private static final LocalDate FROM = LocalDate.of(2026, 7, 14);
    private static final LocalDate TO = LocalDate.of(2026, 7, 15);
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 7, 15, 0, 0);

    @TempDir
    Path tempDir;

    private CapitalFlowRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("capital-flow-point-in-time.db")
                + "?foreign_keys=on");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", tempDir.toString());
        initializer.afterPropertiesSet();

        new MarketIntelSchemaMigrator(jdbc, new DataSourceTransactionManager(dataSource)).migrate();
        jdbc.update("INSERT INTO instrument(id,code,type,name,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?)",
                7L, "600519.SH", "STOCK", "贵州茅台", "2026-07-14T09:00", "2026-07-14T09:00");
        jdbc.update("INSERT INTO instrument(id,code,type,name,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?)",
                8L, "000001.SZ", "STOCK", "平安银行", "2026-07-14T09:00", "2026-07-14T09:00");
        repository = new CapitalFlowRepository(jdbc);
    }

    @Test
    void selectsLatestAvailableDailyVersionPerInstrumentAndDateInStableOrder() {
        CapitalFlowPoint versionA = point(7L, FROM, "DAY_1", "primary", "A",
                LocalDateTime.of(2026, 7, 14, 10, 0), "COMPLETE");
        CapitalFlowPoint versionB = point(7L, FROM, "DAY_1", "secondary", "B",
                LocalDateTime.of(2026, 7, 14, 12, 0), "PARTIAL");
        CapitalFlowPoint futureVersion = point(7L, FROM, "DAY_1", "primary", "C",
                LocalDateTime.of(2026, 7, 16, 10, 0), "COMPLETE");
        CapitalFlowPoint tieLowerId = point(8L, FROM, "DAY_1", "primary", "D",
                LocalDateTime.of(2026, 7, 14, 14, 0), "COMPLETE");
        CapitalFlowPoint tieHigherId = point(8L, FROM, "DAY_1", "secondary", "E",
                LocalDateTime.of(2026, 7, 14, 14, 0), "COMPLETE");
        CapitalFlowPoint intraday = point(8L, FROM, "MINUTE_5", "primary", "F",
                LocalDateTime.of(2026, 7, 14, 23, 59), "COMPLETE");
        CapitalFlowPoint upperBoundary = point(7L, TO, "DAY_1", "primary", "G",
                AS_OF, "COMPLETE");
        repository.saveAll(Arrays.asList(versionA, versionB, futureVersion, tieLowerId,
                tieHigherId, intraday, upperBoundary));

        List<CapitalFlowPoint> first = repository.findDailyPointInTime(FROM, TO, AS_OF);
        List<CapitalFlowPoint> second = repository.findDailyPointInTime(FROM, TO, AS_OF);
        List<CapitalFlowPoint> targeted = repository.findDailyPointInTime(
                FROM, TO, AS_OF, Collections.singletonList(7L));

        assertTrue(tieHigherId.getId() > tieLowerId.getId());
        assertEquals(Arrays.asList(versionB.getId(), tieHigherId.getId(), upperBoundary.getId()), ids(first));
        assertEquals(ids(first), ids(second));
        assertEquals("PARTIAL", first.get(0).getQualityStatus());
        assertEquals("secondary", first.get(0).getProviderCode());
        assertEquals(Arrays.asList(versionB.getId(), upperBoundary.getId()), ids(targeted));
        assertTrue(repository.findDailyPointInTime(
                FROM, TO, AS_OF, Collections.<Long>emptyList()).isEmpty());
    }

    @Test
    void rejectsMissingOrReversedArgumentsBeforeAccessingSql() {
        CapitalFlowRepository disconnected = new CapitalFlowRepository(new JdbcTemplate());

        assertThrows(BusinessException.class,
                () -> disconnected.findDailyPointInTime(null, TO, AS_OF));
        assertThrows(BusinessException.class,
                () -> disconnected.findDailyPointInTime(FROM, null, AS_OF));
        assertThrows(BusinessException.class,
                () -> disconnected.findDailyPointInTime(FROM, TO, null));
        assertThrows(BusinessException.class,
                () -> disconnected.findDailyPointInTime(TO, FROM, AS_OF));
    }

    private static CapitalFlowPoint point(Long instrumentId, LocalDate dataDate, String granularity,
                                          String providerCode, String payloadHash,
                                          LocalDateTime retrievedAt, String qualityStatus) {
        CapitalFlowPoint point = new CapitalFlowPoint();
        point.setInstrumentId(instrumentId);
        point.setProviderCode(providerCode);
        point.setGranularity(granularity);
        point.setDataDate(dataDate);
        point.setObservedAt(dataDate.atTime(15, 0));
        point.setCalculationVersion("capital-flow-v1");
        point.setRetrievedAt(retrievedAt);
        point.setPayloadHash(payloadHash);
        point.setQualityStatus(qualityStatus);
        return point;
    }

    private static List<Long> ids(List<CapitalFlowPoint> points) {
        return points.stream().map(CapitalFlowPoint::getId).collect(Collectors.toList());
    }
}
