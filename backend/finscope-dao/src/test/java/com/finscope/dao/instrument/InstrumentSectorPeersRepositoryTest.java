package com.finscope.dao.instrument;

import com.finscope.domain.instrument.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentSectorPeersRepositoryTest {
    @TempDir
    Path directory;
    private JdbcTemplate jdbc;
    private InstrumentRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + directory.resolve("peers.db"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE instrument(id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, type TEXT, "
                + "name TEXT, market TEXT, aliases TEXT, sector_code TEXT, chain_tags TEXT, "
                + "created_at TEXT, updated_at TEXT)");
        repository = new InstrumentRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void queriesOnlyExactLocalStockSectorAndMarketWithBoundedStableLimit() {
        insert("600000", "STOCK", "SH", "SECTOR_A");
        for (int index = 1; index <= 12; index++) {
            insert(String.format("600%03d", index), "STOCK", "SH", "SECTOR_A");
        }
        insert("600999", "FUND", "SH", "SECTOR_A");
        insert("000001", "STOCK", "SZ", "SECTOR_A");
        insert("600998", "STOCK", "SH", "SECTOR_B");

        List<Instrument> result = repository.findSectorPeers("SECTOR_A", "SH", 1L, 100);

        assertEquals(8, result.size());
        assertEquals("600012", result.get(0).getCode());
        assertTrue(result.stream().allMatch(value -> "STOCK".equals(value.getType())
                && "SECTOR_A".equals(value.getSectorCode()) && "SH".equals(value.getMarket())
                && value.getId() != 1L));
        assertEquals(2, repository.findSectorPeers("SECTOR_A", "SH", 1L, 2).size());
    }

    @Test
    void bindsSectorTextInsteadOfTreatingItAsSql() {
        insert("600001", "STOCK", "SH", "SECTOR_A");
        assertTrue(repository.findSectorPeers("SECTOR_A' OR 1=1 --", "SH", 0L, 8).isEmpty());
    }

    @Test
    void missingSectorMarketIdOrNonpositiveLimitReturnsNoPeers() {
        insert("600001", "STOCK", "SH", "SECTOR_A");
        assertTrue(repository.findSectorPeers(" ", "SH", 0L, 8).isEmpty());
        assertTrue(repository.findSectorPeers(null, "SH", 0L, 8).isEmpty());
        assertTrue(repository.findSectorPeers("SECTOR_A", null, 0L, 8).isEmpty());
        assertTrue(repository.findSectorPeers("SECTOR_A", "SH", null, 8).isEmpty());
        assertTrue(repository.findSectorPeers("SECTOR_A", "SH", 0L, 0).isEmpty());
    }

    private void insert(String code, String type, String market, String sector) {
        jdbc.update("INSERT INTO instrument(code,type,name,market,sector_code) VALUES(?,?,?,?,?)",
                code, type, code, market, sector);
    }
}
