package com.finscope.dao.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.financials.BrokerResearchClaim;
import com.finscope.domain.financials.BrokerResearchForecast;
import com.finscope.domain.financials.BrokerResearchReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrokerResearchPersistenceTest {
    @TempDir Path tempDir;
    private JdbcTemplate jdbc;
    private BrokerResearchReportRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("broker-research.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY,code TEXT,type TEXT,name TEXT)");
        jdbc.update("INSERT INTO instrument VALUES(7,'600519','STOCK','贵州茅台')");
        FinancialSchemaMigrator migrator = new FinancialSchemaMigrator(
                jdbc, new DataSourceTransactionManager(dataSource));
        migrator.migrate();
        migrator.migrate();
        repository = new BrokerResearchReportRepository(jdbc,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void migrationCreatesExternalResearchTablesExactlyOnce() {
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=303", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                        "AND name IN ('broker_research_report','broker_research_forecast','broker_research_claim')",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=304", Integer.class));
    }

    @Test
    void reportForecastsAndClaimsRoundTripWithoutLoadingFullTextInList() {
        BrokerResearchReport report = report();
        BrokerResearchForecast forecast = new BrokerResearchForecast();
        forecast.setMetricCode("REVENUE");
        forecast.setMetricLabel("营业收入");
        forecast.setForecastPeriod(LocalDate.of(2026, 12, 31));
        forecast.setForecastValue(new BigDecimal("100000000000"));
        forecast.setUnit("CNY");
        forecast.setSourceQuote("预计2026年收入1000亿元");
        forecast.setSourcePage(18);
        BrokerResearchClaim claim = new BrokerResearchClaim();
        claim.setCategory("INVESTMENT_THESIS");
        claim.setTitle("高端产品结构升级");
        claim.setDetail("产品结构升级有望支撑毛利率");
        claim.setClaimType("OPINION");
        claim.setSourceQuote("产品结构持续升级");
        claim.setSourcePage(8);
        claim.setFinancialMetricCode("GROSS_MARGIN");

        repository.save(report, Arrays.asList(forecast), Arrays.asList(claim));

        BrokerResearchReport loaded = repository.findById(report.getId())
                .orElseThrow(AssertionError::new);
        assertNotNull(report.getId());
        assertEquals("贵州茅台深度报告", loaded.getTitle());
        assertEquals("研报完整正文", loaded.getExtractedText());
        assertEquals(1, repository.findForecasts(report.getId()).size());
        assertEquals(new BigDecimal("100000000000"),
                repository.findForecasts(report.getId()).get(0).getForecastValue());
        assertEquals("GROSS_MARGIN",
                repository.findClaims(report.getId()).get(0).getFinancialMetricCode());
        assertEquals(1, repository.findByInstrument(7L).size());
        assertEquals(null, repository.findByInstrument(7L).get(0).getExtractedText());
    }

    @Test
    void fileHashMakesRepeatedUploadReuseTheExistingReport() {
        BrokerResearchReport first = report();
        BrokerResearchReport second = report();

        repository.save(first, java.util.Collections.emptyList(), java.util.Collections.emptyList());
        repository.save(second, java.util.Collections.emptyList(), java.util.Collections.emptyList());

        assertEquals(first.getId(), second.getId());
        assertEquals(1, repository.findByInstrument(7L).size());
    }

    @Test
    void sourceIdentityCanBeFoundAndCannotBeInsertedTwice() {
        BrokerResearchReport first = report();
        first.setFileHash("first-hash");
        first.setSourceType("EASTMONEY");
        first.setSourceUrl("https://pdf.dfcfw.com/pdf/H3_AP1_1.pdf");
        BrokerResearchReport second = report();
        second.setFileHash("second-hash");
        second.setSourceType("EASTMONEY");
        second.setSourceUrl(first.getSourceUrl());

        repository.save(first, java.util.Collections.emptyList(), java.util.Collections.emptyList());

        assertEquals(first.getId(), repository.findBySourceUrl(
                "EASTMONEY", first.getSourceUrl()).orElseThrow(AssertionError::new).getId());
        assertThrows(org.springframework.dao.DataAccessException.class, () ->
                repository.save(second, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList()));
    }

    private BrokerResearchReport report() {
        BrokerResearchReport report = new BrokerResearchReport();
        report.setInstrumentId(7L);
        report.setLinkedFinancialReportId(null);
        report.setTitle("贵州茅台深度报告");
        report.setInstitution("测试证券");
        report.setAnalyst("张三");
        report.setPublishedDate(LocalDate.of(2026, 4, 20));
        report.setReportType("DEEP_DIVE");
        report.setRating("买入");
        report.setTargetPrice(new BigDecimal("1800"));
        report.setTargetPriceCurrency("CNY");
        report.setSourceType("UPLOAD");
        report.setOriginalFileName("maotai.pdf");
        report.setRelativePath("7/hash.pdf");
        report.setFileHash("same-hash");
        report.setPageCount(30);
        report.setParseStatus("PARSED");
        report.setAnalysisStatus("COMPLETED");
        report.setQualityLevel("HIGH");
        report.setExtractedText("研报完整正文");
        report.setAnalysisJson("{\"executiveSummary\":[\"核心判断\"]}");
        return report;
    }
}
