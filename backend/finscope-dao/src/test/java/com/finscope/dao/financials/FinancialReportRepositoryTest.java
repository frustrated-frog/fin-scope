package com.finscope.dao.financials;

import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialQualityStatus;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialStatementType;
import com.finscope.domain.financials.FinancialValueOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FinancialReportRepositoryTest {
    @TempDir
    Path tempDir;

    private JdbcTemplate jdbc;
    private FinancialReportRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("financials.db") + "?foreign_keys=on");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY,code TEXT,type TEXT,name TEXT)");
        jdbc.update("INSERT INTO instrument VALUES(7,'600519','STOCK','贵州茅台')");
        FinancialSchemaMigrator migrator = new FinancialSchemaMigrator(
                jdbc, new DataSourceTransactionManager(dataSource));
        migrator.migrate();
        migrator.migrate();
        repository = new FinancialReportRepository(jdbc);
    }

    @Test
    void reportAndLineItemsRoundTripWithDecimalPrecision() {
        FinancialReport report = report();
        repository.saveReport(report);
        repository.replaceLineItems(report.getId(), "AKSHARE", Arrays.asList(
                line(report.getId(), FinancialStatementType.INCOME, "营业收入",
                        "REVENUE", "1200000000.12", 10),
                line(report.getId(), FinancialStatementType.INCOME, "归母净利润",
                        "NET_PROFIT_PARENT", "210000000", 20),
                line(report.getId(), FinancialStatementType.BALANCE_SHEET, "应收账款",
                        "ACCOUNTS_RECEIVABLE", "220000000", 10)
        ));

        List<FinancialReport> reports = repository.findReports(7L);
        List<FinancialLineItem> income = repository.findLineItems(
                report.getId(), FinancialStatementType.INCOME);

        assertEquals(1, reports.size());
        assertEquals(FinancialReportType.HALF_YEAR, reports.get(0).getReportType());
        assertEquals(2, income.size());
        assertEquals(new BigDecimal("1200000000.12"), income.get(0).getNormalizedValue());
        assertEquals("REVENUE", income.get(0).getConceptCode());
        assertNotNull(income.get(0).getId());
    }

    @Test
    void saveReportIsIdempotentForTheSameCompanyPeriodAndScope() {
        FinancialReport first = report();
        FinancialReport second = report();

        repository.saveReport(first);
        repository.saveReport(second);

        assertEquals(first.getId(), second.getId());
        assertEquals(1, repository.findReports(7L).size());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM schema_migration WHERE version=200",
                Integer.class));
    }

    private FinancialReport report() {
        FinancialReport report = new FinancialReport();
        report.setInstrumentId(7L);
        report.setPeriodEnd(LocalDate.of(2026, 6, 30));
        report.setReportType(FinancialReportType.HALF_YEAR);
        report.setScope("CONSOLIDATED");
        report.setCurrency("CNY");
        report.setPublishedAt(LocalDateTime.of(2026, 8, 20, 18, 0));
        report.setAudited(false);
        report.setQualityStatus(FinancialQualityStatus.FRESH);
        report.setSourceCode("AKSHARE");
        return report;
    }

    private FinancialLineItem line(Long reportId, FinancialStatementType type, String sourceLabel,
                                   String conceptCode, String value, int order) {
        FinancialLineItem item = new FinancialLineItem();
        item.setReportId(reportId);
        item.setStatementType(type);
        item.setSourceLabel(sourceLabel);
        item.setConceptCode(conceptCode);
        item.setPeriodRole(type == FinancialStatementType.BALANCE_SHEET
                ? "CURRENT_PERIOD_END" : "CURRENT_YTD");
        item.setNormalizedValue(new BigDecimal(value));
        item.setValueOrigin(FinancialValueOrigin.REPORTED);
        item.setSourceCode("AKSHARE");
        item.setDisplayOrder(order);
        item.setQualityStatus(FinancialQualityStatus.FRESH);
        return item;
    }
}
