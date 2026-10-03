package com.finscope.dao.marketpulse;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.dao.investmentobservation.ReactionSchemaMigrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.sqlite.SQLiteDataSource;
import java.nio.file.Path;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PersonalMarketRepositoryTest {
    @TempDir
    Path temp;

    @Test
    void scopesToWatchedStocksAndRequestedDateWithoutLosingExistingReports() throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + temp.resolve("test.db"));
        var jdbc = new JdbcTemplate(source);
        var initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", temp.toString());
        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();
        var migrator = new ReactionSchemaMigrator();
        ReflectionTestUtils.setField(migrator, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(migrator, "transactionManager", new DataSourceTransactionManager(source));
        migrator.afterPropertiesSet();
        jdbc.update("INSERT INTO instrument(code,type,name,market,created_at,updated_at) VALUES('600519','STOCK','测试公司','SH','2026-09-01','2026-09-01')");
        jdbc.update("INSERT INTO watchlist_item(instrument_id,created_at,focus_reason) SELECT id,'2026-09-01','订单' FROM instrument WHERE code='600519'");
        for (String code : new String[]{"600519", "600000"}) {
            for (String date : new String[]{"2026-09-01", "2026-09-29", "2026-10-05"}) {
                jdbc.update("INSERT INTO attribution_report(instrument_code,instrument_type,report_date,status,summary,created_at,updated_at) VALUES(?,'STOCK',?,'COMPLETED','归因摘要',?,?)", code,date,date,date);
            }
        }
        jdbc.update("INSERT INTO investment_reaction_sample(instrument_code,state,snapshot_json,registered_at,source_identity) "
                + "VALUES('600519.SH','OBSERVING',?,'2026-09-29','event:one')",
                "{\"title\":\"新订单\",\"publishedAt\":\"2026-09-29T10:00:00\"}");
        Long sample = jdbc.queryForObject("SELECT id FROM investment_reaction_sample WHERE source_identity='event:one'", Long.class);
        jdbc.update("INSERT INTO investment_reaction_change(sample_id,revision,event_key,change_type,trade_date,detected_at,summary,snapshot_json) VALUES(?,1,'event:one','UPDATED','2026-09-30','2026-09-30T15:30:00','观察进展','{}')",sample);
        var repo = new PersonalMarketRepository();
        ReflectionTestUtils.setField(repo,"jdbcTemplate",jdbc);
        var values = repo.findChanges(LocalDate.parse("2026-09-30"));
        assertEquals(3, values.size());
        assertTrue(values.stream().allMatch(value -> value.getCode().equals("600519")));
        assertTrue(values.stream().allMatch(value -> value.getReason().equals("订单")));
        assertTrue(values.stream().anyMatch(value -> value.getReportId()!=null));
        assertTrue(values.stream().anyMatch(value -> "event:one".equals(value.getEventKey())));
        jdbc.update("UPDATE investment_reaction_sample SET excluded=1");
        assertEquals(1,repo.findChanges(LocalDate.parse("2026-09-30")).size());
        jdbc.update("DELETE FROM watchlist_item");
        assertTrue(repo.findChanges(LocalDate.parse("2026-09-30")).isEmpty());
    }
}
