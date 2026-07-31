package com.finscope.dao.radar;

import com.finscope.domain.radar.RadarEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadarEvidenceRepositoryTest {
    @TempDir Path tempDir;
    private RadarEvidenceRepository repository;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource(); dataSource.setUrl("jdbc:sqlite:"+tempDir.resolve("evidence.db"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE radar_evidence(id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,tool_code TEXT NOT NULL,"
                + "evidence_type TEXT,title TEXT NOT NULL,summary TEXT,url TEXT,source_name TEXT,source_tier TEXT,published_at TEXT,created_at TEXT NOT NULL)");
        repository = new RadarEvidenceRepository(jdbc);
    }

    @Test
    void replacesEvidenceAsOneEventScopedSnapshot() {
        repository.replaceForEvent(3L, Arrays.asList(evidence("公告", "https://example.com/a"), evidence("新闻", "https://example.com/b")));
        repository.replaceForEvent(3L, Arrays.asList(evidence("更新", "https://example.com/c")));

        assertEquals(1, repository.findByEventId(3L).size());
        assertEquals("更新", repository.findByEventId(3L).get(0).getTitle());
    }

    private RadarEvidence evidence(String title,String url){RadarEvidence value=new RadarEvidence();value.setToolCode("public_news_search");
        value.setEvidenceType("PUBLIC_NEWS");value.setTitle(title);value.setSummary("摘要");value.setUrl(url);value.setSourceName("example.com");
        value.setSourceTier("T2");value.setCreatedAt(LocalDateTime.of(2026,7,31,16,0));return value;}
}
