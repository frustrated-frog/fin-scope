package com.finscope.service.research.report;

import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.source.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThesisQueryExpansionServiceTest {
    @Test
    void createsTwoBoundedTransientGoogleNewsQueriesPerRound() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectName("半导体设备");
        thesis.setQuestion("科技板块回落后周期是否还能持续");

        List<Source> sources = new ThesisQueryExpansionService().queries(thesis, 2);

        assertEquals(2, sources.size());
        assertTrue(sources.get(0).getUrl().startsWith("https://news.google.com/rss/search?q="));
        assertTrue(sources.get(0).getUrl().contains("%E5%8D%8A%E5%AF%BC%E4%BD%93%E8%AE%BE%E5%A4%87"));
        assertEquals("RSS", sources.get(0).getType());
        assertEquals(5, sources.get(0).getMaxItemsPerRun());
    }
}
