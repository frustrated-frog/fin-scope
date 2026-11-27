package com.finscope.dao.quant;

import com.finscope.domain.quant.paper.ObservedIndustry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperIndustryObservationTest {
    @TempDir
    Path temp;

    @Test
    void onlyUsesUnambiguousIndustryKnownAtTheValuation() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + temp.resolve("industries.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE stock_discovery_run(id INTEGER, business_date TEXT, completed_at TEXT, status TEXT)");
        jdbc.execute("CREATE TABLE stock_discovery_candidate(run_id INTEGER, instrument_code TEXT, sector_codes_json TEXT)");
        jdbc.execute("CREATE TABLE stock_discovery_sector(run_id INTEGER, sector_code TEXT, sector_name TEXT, category TEXT, retrieved_at TEXT)");
        jdbc.execute("INSERT INTO stock_discovery_run VALUES (1,'2026-11-25','2026-11-25T15:00:00','SUCCEEDED'),"
                + "(2,'2026-11-26','2026-11-26T16:00:00','SUCCEEDED')");
        for (int id : List.of(1, 2)) {
            jdbc.update("INSERT INTO stock_discovery_candidate VALUES(?, '601288.SH', '[\"a\",\"b\"]')", id);
            jdbc.update("INSERT INTO stock_discovery_sector VALUES(?, 'a', ?, 'INDUSTRY','2026-11-25T12:00:00')",
                    id, id == 1 ? "银行" : "变更行业");
            jdbc.update("INSERT INTO stock_discovery_sector VALUES(?, 'b','主题','CONCEPT','2026-11-25T12:00:00')", id);
        }
        PaperStockNameRepository repo = new PaperStockNameRepository();
        ReflectionTestUtils.setField(repo, "jdbcTemplate", jdbc);
        List<String> codes = List.of("601288", "000001");
        assertEquals(Map.of("601288", new ObservedIndustry("银行", LocalDate.parse("2026-11-25"))),
                repo.industriesAt(codes, LocalDateTime.parse("2026-11-26T15:00")));
        assertEquals("变更行业", repo.industriesAt(codes, LocalDateTime.parse("2026-11-26T16:00")).get("601288").name());
        jdbc.update("UPDATE stock_discovery_sector SET category='INDUSTRY' WHERE run_id=2 AND sector_code='b'");
        assertEquals(Map.of(), repo.industriesAt(codes, LocalDateTime.parse("2026-11-26T16:00")));
    }
}
