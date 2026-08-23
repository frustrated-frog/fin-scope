package com.finscope.dao.marketpulse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finscope.common.enums.marketpulse.MarketLiquidityState;
import com.finscope.common.enums.marketpulse.MarketPulseQualityStatus;
import com.finscope.common.enums.marketpulse.MarketRiskAppetiteState;
import com.finscope.common.enums.marketpulse.MarketRotationState;
import com.finscope.common.enums.marketpulse.MarketStage;
import com.finscope.common.enums.marketpulse.MarketTrendState;
import com.finscope.common.enums.marketpulse.SectorRotationStage;
import com.finscope.domain.marketpulse.MarketPulseWorkspace;
import com.finscope.domain.marketpulse.MarketRegimeSnapshot;
import com.finscope.domain.marketpulse.SectorRotationItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPulseRepositoryTest {
    private MarketPulseRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + Files.createTempDirectory("market-pulse").resolve("finance.db"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        MarketPulseSchemaMigrator migrator = new MarketPulseSchemaMigrator();
        ReflectionTestUtils.setField(migrator, "jdbcTemplate", jdbcTemplate);
        migrator.migrate();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        repository = new MarketPulseRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(repository, "objectMapper", objectMapper);
    }

    @Test
    void replacesTheSameBusinessDateWithoutDuplicatingSnapshots() {
        repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 21), 71));
        repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 21), 82));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM market_regime_snapshot", Integer.class));
        MarketPulseWorkspace stored = repository.findWorkspace(LocalDate.of(2026, 8, 21))
                .orElseThrow(AssertionError::new);
        assertEquals(82, stored.getRegime().getConfidenceScore());
        assertEquals(1, stored.getSectors().size());
    }

    @Test
    void listsUniqueBusinessDatesInDescendingOrder() {
        repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 19), 60));
        repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 21), 80));
        repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 20), 70));

        assertEquals(Arrays.asList(
                        LocalDate.of(2026, 8, 21),
                        LocalDate.of(2026, 8, 20)),
                repository.findRecentDates(2));
        assertTrue(repository.findLatestWorkspace().isPresent());
    }

    @Test
    void ignoresSnapshotsAfterTheLatestValidTradingDate() {
        repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 21), 80));
        repository.saveWorkspace(workspace(LocalDate.of(2026, 8, 23), 99));

        MarketPulseWorkspace latest = repository.findLatestWorkspace(LocalDate.of(2026, 8, 21))
                .orElseThrow(AssertionError::new);

        assertEquals(LocalDate.of(2026, 8, 21), latest.getBusinessDate());
        assertEquals(Arrays.asList(LocalDate.of(2026, 8, 21)),
                repository.findRecentDates(10, LocalDate.of(2026, 8, 21)));
    }

    private MarketPulseWorkspace workspace(LocalDate businessDate, int confidence) {
        MarketRegimeSnapshot regime = new MarketRegimeSnapshot();
        regime.setBusinessDate(businessDate);
        regime.setTrendState(MarketTrendState.RANGE);
        regime.setLiquidityState(MarketLiquidityState.SHRINKING);
        regime.setRiskAppetiteState(MarketRiskAppetiteState.LOW);
        regime.setRotationState(MarketRotationState.FAST);
        regime.setMarketStage(MarketStage.POST_SELL_OFF_REPAIR);
        regime.setConfidenceScore(confidence);
        regime.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        regime.setExplanation("急跌后的缩量修复");
        regime.setSourceFingerprint("market-" + confidence);
        regime.setCalculatedAt(LocalDateTime.of(businessDate, java.time.LocalTime.of(16, 0)));

        SectorRotationItem sector = new SectorRotationItem();
        sector.setSectorCode("881144");
        sector.setSectorName("医药生物");
        sector.setReturn1d(3.8D);
        sector.setRotationScore(84);
        sector.setStage(SectorRotationStage.ACCELERATING);

        MarketPulseWorkspace value = new MarketPulseWorkspace();
        value.setBusinessDate(businessDate);
        value.setRegime(regime);
        value.setSectors(Arrays.asList(sector));
        value.setQualityStatus(MarketPulseQualityStatus.PARTIAL);
        value.setGeneratedAt(regime.getCalculatedAt());
        return value;
    }
}
