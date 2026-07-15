package com.finscope.service.factorresearch;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.factorresearch.FactorResearchSchemaMigrator;
import com.finscope.dao.factorresearch.QuantCapitalFlowRepository;
import com.finscope.dao.factorresearch.QuantDatasetPartitionRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.dao.quant.QuantDatasetRepository;
import com.finscope.dao.quant.QuantMarketDataRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantUniverseMember;
import com.finscope.service.quant.data.QuantDatasetFingerprint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CapitalFlowFreezeServiceTransactionTest {
    @TempDir
    Path tempDir;

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private CapitalFlowFreezeService service;
    private CapitalFlowRepository sourceFlows;
    private QuantDatasetRepository datasets;
    private QuantMarketDataRepository marketData;
    private InstrumentRepository instruments;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "capital-freeze-transaction-test",
                Collections.<String, Object>singletonMap("test.data-root", tempDir.toString())));
        context.register(TransactionTestConfiguration.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(CapitalFlowFreezeService.class);
        sourceFlows = context.getBean(CapitalFlowRepository.class);
        datasets = context.getBean(QuantDatasetRepository.class);
        marketData = context.getBean(QuantMarketDataRepository.class);
        instruments = context.getBean(InstrumentRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void optimisticStateTransitionFailureRollsBackRowsAndPartition() {
        LocalDate date = LocalDate.of(2026, 7, 14);
        QuantDataset dataset = datasets.save(dataset());
        Instrument instrument = new Instrument();
        instrument.setCode("600519"); instrument.setMarket("SH"); instrument.setType("STOCK"); instrument.setName("贵州茅台");
        instruments.save(instrument);
        QuantUniverseMember member = new QuantUniverseMember();
        member.setDatasetId(dataset.getId()); member.setTradeDate(date); member.setInstrumentCode("600519.SH");
        member.setMember(true); member.setSourceKind("POINT_IN_TIME");
        marketData.insertUniverseMembers(Collections.singletonList(member));
        marketData.insertBars(Collections.singletonList(bar(dataset.getId(), date)));
        when(sourceFlows.findDailyPointInTime(org.mockito.ArgumentMatchers.eq(date),
                org.mockito.ArgumentMatchers.eq(date),
                org.mockito.ArgumentMatchers.eq(date.plusDays(1).atTime(9, 0)),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singletonList(point(instrument.getId(), date)));

        assertThrows(BusinessException.class, () -> service.freeze(
                dataset.getId(), date, date, date.plusDays(1).atTime(9, 0)));

        assertEquals(0, count("quant_capital_flow_daily"));
        assertEquals(0, count("quant_dataset_partition"));
        assertEquals(0L, datasets.findById(dataset.getId()).orElseThrow(AssertionError::new).getRevision());
    }

    private int count(String table) {
        Integer result = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return result == null ? 0 : result;
    }

    private QuantDataset dataset() {
        QuantDataset value = new QuantDataset();
        value.setName("rollback research"); value.setMarket("A_SHARE"); value.setUniverseType("CUSTOM");
        value.setSourceType("MANUAL_IMPORT"); value.setDataKind("REAL"); value.setDatasetLevel("RESEARCH");
        value.setFingerprintVersion("quant-dataset-v2"); value.setPartitionManifest("[]"); value.setStatus("BUILDING");
        return value;
    }

    private CapitalFlowPoint point(Long instrumentId, LocalDate date) {
        CapitalFlowPoint value = new CapitalFlowPoint();
        value.setId(99L); value.setInstrumentId(instrumentId); value.setProviderCode("EASTMONEY");
        value.setGranularity("DAY_1"); value.setDataDate(date); value.setObservedAt(date.atTime(15, 0));
        value.setRetrievedAt(date.atTime(18, 0)); value.setPayloadHash("payload");
        value.setCalculationVersion("v3"); value.setQualityStatus("COMPLETE");
        value.setIntervalTradeAmount(new BigDecimal("1000")); value.setMainNetInflow(new BigDecimal("100"));
        return value;
    }

    private QuantDailyBar bar(Long datasetId, LocalDate date) {
        QuantDailyBar value = new QuantDailyBar();
        value.setDatasetId(datasetId); value.setTradeDate(date); value.setInstrumentCode("600519.SH");
        value.setOpen(new BigDecimal("1500")); value.setHigh(new BigDecimal("1520"));
        value.setLow(new BigDecimal("1490")); value.setClose(new BigDecimal("1510"));
        value.setAdjustedClose(new BigDecimal("1510")); value.setVolume(new BigDecimal("100000"));
        value.setAmount(new BigDecimal("151000000")); value.setTradeStatus("TRADING");
        return value;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionTestConfiguration {
        @Bean
        DataSource dataSource(@Value("${test.data-root}") String root) {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + Paths.get(root).resolve("finance.db") + "?foreign_keys=on");
            return dataSource;
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        DatabaseInitializer databaseInitializer(JdbcTemplate jdbc, @Value("${test.data-root}") String root) {
            DatabaseInitializer value = new DatabaseInitializer();
            ReflectionTestUtils.setField(value, "jdbcTemplate", jdbc);
            ReflectionTestUtils.setField(value, "dataRoot", root);
            return value;
        }

        @Bean
        FactorResearchSchemaMigrator factorResearchSchemaMigrator(JdbcTemplate jdbc,
                PlatformTransactionManager transactionManager, DatabaseInitializer databaseInitializer) {
            return new FactorResearchSchemaMigrator(jdbc, transactionManager);
        }

        @Bean
        QuantDatasetRepository datasets(JdbcTemplate jdbc) {
            QuantDatasetRepository value = new FailingQuantDatasetRepository();
            ReflectionTestUtils.setField(value, "jdbcTemplate", jdbc);
            return value;
        }

        @Bean
        QuantMarketDataRepository marketData(JdbcTemplate jdbc) {
            QuantMarketDataRepository value = new QuantMarketDataRepository();
            ReflectionTestUtils.setField(value, "jdbcTemplate", jdbc);
            return value;
        }

        @Bean
        InstrumentRepository instruments(JdbcTemplate jdbc) {
            InstrumentRepository value = new InstrumentRepository();
            ReflectionTestUtils.setField(value, "jdbcTemplate", jdbc);
            return value;
        }

        @Bean CapitalFlowRepository sourceFlows() { return mock(CapitalFlowRepository.class); }
        @Bean QuantCapitalFlowRepository capitalFlows(JdbcTemplate jdbc, PlatformTransactionManager manager) {
            return new QuantCapitalFlowRepository(jdbc, manager);
        }

        @Bean
        QuantDatasetPartitionRepository partitions(JdbcTemplate jdbc) {
            QuantDatasetPartitionRepository value = new QuantDatasetPartitionRepository();
            ReflectionTestUtils.setField(value, "jdbcTemplate", jdbc);
            return value;
        }

        @Bean QuantDatasetFingerprint fingerprint() { return new QuantDatasetFingerprint(); }
        @Bean CapitalFlowFreezeService capitalFlowFreezeService() { return new CapitalFlowFreezeService(); }
    }

    static final class FailingQuantDatasetRepository extends QuantDatasetRepository {
        @Override
        public boolean updateResearchState(Long id, LocalDate start, LocalDate end, String status,
                LocalDateTime asOfTime, String fingerprintVersion, String partitionManifest,
                String fingerprint, String qualitySummary, long revision) {
            return false;
        }
    }
}
