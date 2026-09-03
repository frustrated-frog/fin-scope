package com.finscope.service.majorevent;

import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.majorevent.MajorEventRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.majorevent.MajorEvent;
import com.finscope.domain.majorevent.MajorEventCreateCommand;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.service.news.NewsFeedItem;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
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
        MajorEventService service = service(events, articles, radar, mock(NewsFeedService.class));
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

    @Test
    void createsNewsSnapshotOnlyFromTheCurrentCacheItem() {
        MajorEventRepository events = mock(MajorEventRepository.class);
        NewsFeedService news = mock(NewsFeedService.class);
        NewsFeedItem item = new NewsFeedItem("CLS:1", "FLASH", "缓存中的标题", "缓存中的正文",
                "https://example.com/news", LocalDateTime.of(2026, 8, 4, 10, 0),
                "CLS", "财联社", "T1", "MACRO", "宏观", null, null);
        when(news.load("ALL", 100)).thenReturn(new NewsFeedSnapshot(Collections.singletonList(item),
                Collections.emptyList(), LocalDateTime.now(), 1));
        when(events.findByOrigin("NEWS_ITEM", "CLS:1")).thenReturn(Optional.empty());
        when(events.save(any(MajorEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MajorEventService service = service(events, mock(ArticleRepository.class), mock(RadarRepository.class), news);
        MajorEventCreateCommand command = new MajorEventCreateCommand();
        command.setOriginType("NEWS_ITEM");
        command.setOriginKey("CLS:1");
        MajorEvent saved = service.create(command);

        assertEquals("缓存中的标题", saved.getTitle());
        assertEquals("缓存中的正文", saved.getSummary());
        assertEquals("财联社", saved.getSourceName());
        assertEquals(LocalDate.of(2026, 8, 4), saved.getOccurredDate());
    }

    @Test
    void usesStableRadarEventKeyForThePersistentOrigin() {
        MajorEventRepository events = mock(MajorEventRepository.class);
        RadarRepository radar = mock(RadarRepository.class);
        RadarEvent source = new RadarEvent();
        source.setEventKey("central-bank-rate-cut");
        source.setCanonicalTitle("央行降息");
        source.setFirstSeenAt(LocalDateTime.of(2026, 8, 4, 9, 0));
        when(radar.findEventByKey("central-bank-rate-cut")).thenReturn(Optional.of(source));
        when(events.findByOrigin("RADAR_EVENT", "central-bank-rate-cut")).thenReturn(Optional.empty());
        when(events.save(any(MajorEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MajorEventService service = service(events, mock(ArticleRepository.class), radar, mock(NewsFeedService.class));
        MajorEventCreateCommand command = new MajorEventCreateCommand();
        command.setOriginType("RADAR_EVENT");
        command.setOriginKey("central-bank-rate-cut");

        MajorEvent saved = service.create(command);

        assertEquals("central-bank-rate-cut", saved.getOriginKey());
        assertEquals("央行降息", saved.getTitle());
    }

    private MajorEventService service(MajorEventRepository events, ArticleRepository articles,
                                      RadarRepository radar, NewsFeedService news) {
        MajorEventService service = new MajorEventService();
        ReflectionTestUtils.setField(service, "events", events);
        ReflectionTestUtils.setField(service, "articles", articles);
        ReflectionTestUtils.setField(service, "radar", radar);
        ReflectionTestUtils.setField(service, "news", news);
        return service;
    }
}
