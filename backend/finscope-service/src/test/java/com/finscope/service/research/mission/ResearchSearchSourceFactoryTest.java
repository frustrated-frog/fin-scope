package com.finscope.service.research.mission;

import com.finscope.domain.research.mission.ResearchMissionTask;
import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchSearchSourceFactoryTest {
    @Test
    void createsEphemeralRssSourceFromValidatedSearchTask() {
        ResearchMissionTask task = new ResearchMissionTask();
        task.setTaskKey("search_counter");
        task.setTitle("反方证据搜索");
        task.setToolCode("public_news_search");
        task.setIntent("COUNTER");
        task.setQueryText("AI算力 资本开支 风险 下调");

        Source source = new ResearchSearchSourceFactory().create(task);

        assertEquals("RSS", source.getType());
        assertTrue(source.getName().contains("反方证据搜索"));
        assertTrue(source.getUrl().startsWith("https://news.google.com/rss/search?q="));
        assertFalse(source.isScheduledEnabled());
        assertEquals(5, source.getMaxItemsPerRun());
    }

    @Test
    void refusesUnvalidatedToolOrProtocolQuery() {
        ResearchMissionTask task = new ResearchMissionTask();
        task.setTaskKey("unsafe");
        task.setToolCode("shell");
        task.setQueryText("https://example.com");

        assertThrows(IllegalArgumentException.class, () -> new ResearchSearchSourceFactory().create(task));
    }
}
