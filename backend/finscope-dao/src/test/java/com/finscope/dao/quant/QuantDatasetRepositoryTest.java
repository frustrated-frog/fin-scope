package com.finscope.dao.quant;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.data.QuantFundamentalSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantDatasetRepositoryTest {
    private QuantDatasetRepository datasets;
    private QuantMarketDataRepository marketData;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("finscope-quant-dataset-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + root.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", root.toString());
        initializer.afterPropertiesSet();
        datasets = new QuantDatasetRepository();
        marketData = new QuantMarketDataRepository();
        ReflectionTestUtils.setField(datasets, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(marketData, "jdbcTemplate", jdbc);
    }

    @Test
    void persistsDatasetAndRejectsDuplicateBars() {
        QuantDataset dataset = dataset();
        QuantDataset saved = datasets.save(dataset);
        QuantDailyBar bar = bar(saved.getId(), "600519.SH", LocalDate.of(2024, 5, 6));

        marketData.insertBars(Arrays.asList(bar));

        assertEquals("A_SHARE", datasets.findById(saved.getId()).orElseThrow(AssertionError::new).getMarket());
        assertThrows(DataAccessException.class, () -> marketData.insertBars(Arrays.asList(bar)));
        assertEquals(1, marketData.findBars(saved.getId()).size());
    }

    @Test
    void onlyExposesFundamentalsDisclosedBySignalDate() {
        QuantDataset saved = datasets.save(dataset());
        marketData.insertFundamentals(Arrays.asList(
                fundamental(saved.getId(), LocalDate.of(2024, 4, 30), new BigDecimal("0.18")),
                fundamental(saved.getId(), LocalDate.of(2024, 8, 30), new BigDecimal("0.21"))));

        QuantFundamentalSnapshot visible = marketData.latestVisibleFundamental(
                saved.getId(), "600519.SH", LocalDate.of(2024, 5, 1)).orElseThrow(AssertionError::new);

        assertEquals(LocalDate.of(2024, 4, 30), visible.getDisclosedAt());
        assertEquals(new BigDecimal("0.18"), visible.getRoe());
        assertTrue(!marketData.latestVisibleFundamental(
                saved.getId(), "600519.SH", LocalDate.of(2024, 4, 29)).isPresent());
    }

    private QuantDataset dataset() {
        QuantDataset value = new QuantDataset();
        value.setName("沪深股票学习集");
        value.setMarket("A_SHARE");
        value.setUniverseType("CUSTOM");
        value.setSourceType("MANUAL_IMPORT");
        value.setDataKind("REAL");
        value.setStatus("READY");
        return value;
    }

    private QuantDailyBar bar(Long datasetId, String code, LocalDate date) {
        QuantDailyBar value = new QuantDailyBar();
        value.setDatasetId(datasetId);
        value.setInstrumentCode(code);
        value.setTradeDate(date);
        value.setOpen(new BigDecimal("100"));
        value.setHigh(new BigDecimal("105"));
        value.setLow(new BigDecimal("99"));
        value.setClose(new BigDecimal("103"));
        value.setAdjustedClose(new BigDecimal("103"));
        value.setVolume(new BigDecimal("10000"));
        value.setAmount(new BigDecimal("1030000"));
        value.setTradeStatus("TRADING");
        return value;
    }

    private QuantFundamentalSnapshot fundamental(Long datasetId, LocalDate disclosedAt, BigDecimal roe) {
        QuantFundamentalSnapshot value = new QuantFundamentalSnapshot();
        value.setDatasetId(datasetId);
        value.setInstrumentCode("600519.SH");
        value.setReportPeriod(LocalDate.of(2024, 3, 31));
        value.setDisclosedAt(disclosedAt);
        value.setRoe(roe);
        value.setPe(new BigDecimal("20"));
        value.setPb(new BigDecimal("4"));
        value.setMarketCap(new BigDecimal("1000000000"));
        return value;
    }
}
