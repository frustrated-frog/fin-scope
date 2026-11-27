package com.finscope.dao.quant.study;

import com.finscope.common.enums.quant.StudyState;
import com.finscope.dao.config.DatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AutonomousStudyRepositoryTest {
    @Test
    void fencesExpiredWorkersAndRetriesFromRecordedEvidence(@TempDir Path root) throws Exception {
        var ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + root.resolve("study.db"));
        var jdbc = new JdbcTemplate(ds);
        var init = new DatabaseInitializer();
        ReflectionTestUtils.setField(init,"jdbcTemplate",jdbc);
        ReflectionTestUtils.setField(init,"dataRoot",root.toString());
        init.afterPropertiesSet();
        var repo = new AutonomousStudyRepository();
        ReflectionTestUtils.setField(repo,"jdbc",jdbc);
        long id = repo.enqueue("daily:date",1);
        assertEquals(id,repo.enqueue("daily:date",2));
        assertEquals(id,repo.claim("first",3,10));
        assertNull(repo.claim("second",4,15));
        assertTrue(repo.plan(id,"first","snapshot","{}",4));
        assertEquals(id,repo.claim("second",11,20));
        assertFalse(repo.evidence(id,"first","{}",12));
        assertTrue(repo.evidence(id,"second","{}",12));
        repo.fail(id,"second","模型暂不可用",15);
        assertNull(repo.claim("new",16,18));
        assertEquals(id,repo.claim("third",136,200));
        assertEquals("WRITING",repo.find(id).get("state"));
        repo.finish(id,"third", StudyState.PUBLISHED,"{}",null,140);
        assertNull(repo.claim("fourth",250,260));
        long next = repo.enqueue("manual:key",260);
        assertEquals(next,repo.claim("next",261,300));
        assertFalse(repo.plan(next,"next","snapshot","{}",262));
        assertEquals(1,repo.memory().size());
    }
}
