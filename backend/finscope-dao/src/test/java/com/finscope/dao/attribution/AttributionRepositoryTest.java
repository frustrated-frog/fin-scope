package com.finscope.dao.attribution;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.attribution.AttributionDriver;
import com.finscope.domain.attribution.AttributionEvidence;
import com.finscope.domain.attribution.AttributionNarrative;
import com.finscope.domain.attribution.AttributionReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AttributionRepositoryTest {
    private AttributionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-attribution-repository-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        repository = new AttributionRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void returnsLatestCompletedReportMetadataPerInstrument() {
        AttributionReport first = save("600519", "STOCK", LocalDate.of(2026, 7, 11), "旧归因", 1.2, "COMPLETED");
        save("600519", "STOCK", LocalDate.of(2026, 7, 12), "失败归因", -1.0, "FAILED");
        AttributionReport latest = save("600519", "STOCK", LocalDate.of(2026, 7, 12), "最新归因", 2.6, "COMPLETED");
        save("021894", "FUND", LocalDate.of(2026, 7, 11), "基金归因", -3.1, "COMPLETED");

        Map<String, AttributionRepository.AttributionSummaryView> result = repository.findLatestCompletedSummaryViews();

        assertEquals(2, result.size());
        AttributionRepository.AttributionSummaryView stock = result.get("STOCK:600519");
        assertEquals(latest.getId(), stock.getReportId());
        assertEquals(LocalDate.of(2026, 7, 12), stock.getReportDate());
        assertEquals("最新归因", stock.getSummary());
        assertEquals(2.6, stock.getChangePct());
        assertFalse(first.getId().equals(stock.getReportId()));
    }

    @Test
    void historyContainsOnlyCompletedReportsInStableNewestOrder() {
        AttributionReport older = save("600519", "STOCK", LocalDate.of(2026, 7, 11), "旧归因", 1.2, "COMPLETED");
        save("600519", "STOCK", LocalDate.of(2026, 7, 13), "生成中", null, "GENERATING");
        AttributionReport latest = save("600519", "STOCK", LocalDate.of(2026, 7, 12), "最新归因", 2.6, "COMPLETED");
        save("600519", "STOCK", LocalDate.of(2026, 7, 13), "失败", -1.0, "FAILED");

        List<AttributionReport> history = repository.findHistoryByIdentity("600519", "STOCK", 50);

        assertEquals(2, history.size());
        assertEquals(latest.getId(), history.get(0).getId());
        assertEquals(older.getId(), history.get(1).getId());
    }

    @Test
    void persistsNarrativeAndPlainLanguageDriverFields() {
        AttributionReport report = save(
                "603618", "STOCK", LocalDate.of(2026, 7, 30), "主线", -5.76, "GENERATING");
        AttributionNarrative narrative = new AttributionNarrative();
        narrative.setPlainSummary("延期传闻触发担忧，前期涨幅放大抛压。");
        narrative.setEvent("英伟达机架延期传闻出现");
        narrative.setInstrumentLink("公司处于算力硬件相关链条");
        narrative.setWhyToday("传闻与板块回撤在当日集中共振");
        narrative.setCausalSteps(Arrays.asList("延期传闻", "需求预期下调", "板块承压", "股价下跌"));
        narrative.setAmplifiers(Collections.singletonList("前期累计涨幅较大"));
        narrative.setDampeners(Collections.singletonList("公司尚未确认实际订单影响"));
        AttributionDriver driver = new AttributionDriver();
        driver.setClaim("延期传闻");
        driver.setRole("TRIGGER");
        driver.setPlainExplanation("市场担心相关硬件需求推迟。");
        report.setNarrative(narrative);
        report.setDrivers(Collections.singletonList(driver));
        report.setStatus("COMPLETED");

        repository.updateResult(report);
        AttributionReport restored = repository.findById(report.getId()).orElseThrow(AssertionError::new);

        assertEquals("传闻与板块回撤在当日集中共振", restored.getNarrative().getWhyToday());
        assertEquals(Arrays.asList("延期传闻", "需求预期下调", "板块承压", "股价下跌"),
                restored.getNarrative().getCausalSteps());
        assertEquals("TRIGGER", restored.getDrivers().get(0).getRole());
        assertEquals("市场担心相关硬件需求推迟。", restored.getDrivers().get(0).getPlainExplanation());
    }

    @Test
    void deletesReportAndItsEvidence() {
        AttributionReport report = save("600519", "STOCK", LocalDate.of(2026, 7, 13), "待删除", 1.2, "COMPLETED");
        AttributionEvidence evidence = new AttributionEvidence();
        evidence.setReportId(report.getId());
        evidence.setTitle("公司公告");
        repository.saveEvidence(evidence);

        repository.deleteById(report.getId());

        assertFalse(repository.findById(report.getId()).isPresent());
        assertEquals(0, repository.findEvidenceByReportId(report.getId()).size());
    }

    private AttributionReport save(String code, String type, LocalDate date, String summary, Double changePct, String status) {
        AttributionReport report = new AttributionReport();
        report.setInstrumentCode(code);
        report.setInstrumentName(code);
        report.setInstrumentType(type);
        report.setReportDate(date);
        report.setSummary(summary);
        report.setChangePct(changePct);
        report.setStatus(status);
        return repository.createReport(report);
    }
}
