package com.finscope.service.majorevent;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.majorevent.MajorEventCreateCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MajorEventServiceTest {

    @Test
    void createsArticleSnapshotFromStoredArticle() {
        MajorEventRepository events = mock(MajorEventRepository.class);
        ArticleRepository articles = mock(ArticleRepository.class);
        RadarRepository radar = mock(RadarRepository.class);
        MajorEventService service = new MajorEventService(events, articles, radar);
        Article article = new Article();
        article.setId(3L);
        article.setTitle("央行降准");
        article.setSummary("释放长期流动性");
        article.setSourceName("新华社");
        article.setUrl("https://example.com/article");
        article.setCategory("宏观");
        article.setPublishedAt(LocalDateTime.of(2026, 8, 4, 9, 0));
        when(articles.findById(3L)).thenReturn(Optional.of(article));
        when(events.findByOrigin("ARTICLE", "3")).thenReturn(Optional.empty());
        when(events.save(any(MajorEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MajorEvent saved = service.create(MajorEventCreateCommand.article(3L, null, "关注银行负债端变化"));

        assertEquals("ARTICLE", saved.getOriginType());
        assertEquals("3", saved.getOriginKey());
        assertEquals("央行降准", saved.getTitle());
        assertEquals("释放长期流动性", saved.getSummary());
        assertEquals("新华社", saved.getSourceName());
        assertEquals("https://example.com/article", saved.getSourceUrl());
        assertEquals("宏观", saved.getCategoryCode());
        assertEquals(LocalDate.of(2026, 8, 4), saved.getOccurredDate());
        assertEquals("关注银行负债端变化", saved.getNote());
    }
}
