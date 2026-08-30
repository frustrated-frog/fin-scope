package com.finscope.dao.strategy;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.strategy.holding.StockTransaction;
import com.finscope.domain.strategy.holding.StockTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockTransactionRepositoryTest {
    private StockTransactionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-stock-ledger-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();
        jdbcTemplate.update("INSERT INTO instrument(code,type,name,market,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                "600570", "STOCK", "恒生电子", "SH", "2026-08-31T00:00:00", "2026-08-31T00:00:00");
        repository = new StockTransactionRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void savesIdempotentEventAndReturnsStableLedgerOrder() {
        StockTransaction later = transaction("trade-2", LocalDate.of(2026, 8, 29));
        StockTransaction earlier = transaction("trade-1", LocalDate.of(2026, 8, 28));

        repository.save(later);
        repository.save(earlier);

        assertEquals("trade-1", repository.findAll(50).get(0).getClientRequestId());
        assertEquals("trade-2", repository.findAll(50).get(1).getClientRequestId());
        assertTrue(repository.findByClientRequestId("trade-1").isPresent());
        assertEquals(new BigDecimal("100"), repository.findByClientRequestId("trade-1")
                .orElseThrow(AssertionError::new).getQuantity());
    }

    private StockTransaction transaction(String requestId, LocalDate date) {
        StockTransaction value = new StockTransaction();
        value.setClientRequestId(requestId);
        value.setInstrumentId(1L);
        value.setInstrumentCode("600570.SH");
        value.setType(StockTransactionType.BUY);
        value.setTradeDate(date);
        value.setQuantity(new BigDecimal("100"));
        value.setPrice(new BigDecimal("28.50"));
        value.setCommission(new BigDecimal("5.00"));
        value.setStampDuty(BigDecimal.ZERO);
        value.setTransferFee(BigDecimal.ZERO);
        value.setOtherFee(BigDecimal.ZERO);
        value.setCashAmount(BigDecimal.ZERO);
        value.setNote("测试成交");
        return value;
    }
}
