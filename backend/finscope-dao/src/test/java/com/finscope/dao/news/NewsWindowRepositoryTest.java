package com.finscope.dao.news;

import com.finscope.dao.config.DatabaseInitializer;
import com.finscope.domain.research.material.ResearchMaterial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewsWindowRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void retainsRotatingSourcesDeduplicatesFiltersAndExpiresWindow() throws Exception {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("window.db"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        DatabaseInitializer initializer = new DatabaseInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(initializer, "dataRoot", directory.toString());
        initializer.afterPropertiesSet();
        NewsWindowRepository repository = new NewsWindowRepository();
        ReflectionTestUtils.setField(repository, "jdbc", jdbc);
        LocalDateTime now = LocalDateTime.of(2026, 9, 21, 10, 0);
        List<ResearchMaterial> first = new ArrayList<>();
        for (int index = 0; index < 150; index++) {
            first.add(material("CLS", String.valueOf(index), "公司 " + index, now.minusMinutes(index)));
        }
        repository.merge(first, now, now.minusHours(36));
        repository.merge(Collections.singletonList(material("THS", "1", "芯片 100%_增长", now)), now, now.minusHours(36));
        ResearchMaterial corrected = material("CLS", "149", "正文修订", now.minusMinutes(149));
        repository.merge(Collections.singletonList(corrected), now.plusMinutes(1), now.minusHours(36));
        List<String> ids = repository.findIds(now.minusHours(36), now, "ALL", "");
        assertEquals(151, ids.size());
        assertEquals(150, repository.findIds(now.minusHours(36), now, "CLS", "").size());
        assertEquals(Collections.singletonList("THS:1"), repository.findIds(now.minusHours(36), now, "ALL", "100%_"));
        assertEquals("正文修订", repository.findByIds(Collections.singletonList("CLS:149")).get(0).getTitle());
        assertEquals(2, repository.providers(now.minusHours(36), now).size());
        repository.merge(Collections.singletonList(material("CLS", "late", "延迟到达", now.minusMinutes(5))),
                now.plusMinutes(1), now.minusHours(36));
        assertEquals(151, repository.findIds(now.minusHours(36), now, "ALL", "").size());
        assertEquals(152, repository.findIds(now.minusHours(36), now.plusMinutes(1), "ALL", "").size());
        repository.merge(Collections.emptyList(), now.plusHours(37), now.plusHours(1));
        assertTrue(repository.findIds(now.minusHours(36), now.plusHours(37), "ALL", "").isEmpty());
    }

    private ResearchMaterial material(String provider, String id, String title, LocalDateTime time) {
        ResearchMaterial result = new ResearchMaterial();
        result.setProviderCode(provider);
        result.setExternalId(id);
        result.setTitle(title);
        result.setContent("新闻正文");
        result.setPublishedAt(time);
        return result;
    }
}
