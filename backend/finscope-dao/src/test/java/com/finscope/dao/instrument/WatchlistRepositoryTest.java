package com.finscope.dao.instrument;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.instrument.WatchlistItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchlistRepositoryTest {
    private WatchlistRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path dataRoot = Files.createTempDirectory("finscope-watchlist-test");
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dataRoot.resolve("finance.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", dataRoot.toString());
        initializer.afterPropertiesSet();

        insert(jdbc, "600519", "STOCK", "贵州茅台");
        insert(jdbc, "020608", "FUND", "嘉实服务增值行业");
        insert(jdbc, "BK1036", "SECTOR", "半导体");
        jdbc.update("INSERT INTO watchlist_item(instrument_id,sort_order,created_at) "
                + "SELECT id,0,'2026-07-14T10:00:00' FROM instrument");

        repository = new WatchlistRepository();
        ReflectionTestUtils.setField(repository, "jdbcTemplate", jdbc);
    }

    @Test
    void filtersWatchlistRowsByInstrumentType() {
        List<WatchlistItem> investments = repository.findByTypes(Arrays.asList("STOCK", "FUND"));
        List<WatchlistItem> sectors = repository.findByTypes(Collections.singletonList("SECTOR"));

        assertEquals(2, investments.size());
        assertTrue(investments.stream().noneMatch(item -> "SECTOR".equals(item.getType())));
        assertEquals("BK1036", sectors.get(0).getCode());
    }

    @Test
    void findsAndDeletesOnlyTheRequestedCodeAndType() {
        assertTrue(repository.findByCodeAndType("BK1036", "SECTOR").isPresent());
        assertFalse(repository.findByCodeAndType("BK1036", "STOCK").isPresent());

        assertEquals(1, repository.deleteByCodeAndType("BK1036", "SECTOR"));

        assertFalse(repository.findByCodeAndType("BK1036", "SECTOR").isPresent());
        assertEquals(2, repository.findByTypes(Arrays.asList("STOCK", "FUND")).size());
    }

    private void insert(JdbcTemplate jdbc, String code, String type, String name) {
        jdbc.update("INSERT INTO instrument(code,type,name,created_at,updated_at) VALUES(?,?,?,?,?)",
                code, type, name, "2026-07-14T10:00:00", "2026-07-14T10:00:00");
    }
}
