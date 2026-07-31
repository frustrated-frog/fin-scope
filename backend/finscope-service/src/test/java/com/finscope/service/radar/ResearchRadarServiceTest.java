package com.finscope.service.radar;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.radar.RadarEvidenceRepository;
import com.finscope.dao.radar.RadarRepository;
import com.finscope.domain.radar.RadarEvent;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.domain.radar.RadarEvidence;
import com.finscope.domain.agent.AgentRun;
import com.finscope.service.dedupe.FingerprintService;
import com.finscope.service.news.NewsFeedItem;
import com.finscope.service.news.NewsFeedService;
import com.finscope.service.news.NewsFeedSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ResearchRadarServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 16, 0);
    private NewsFeedService news;
    private RadarRepository repository;
    private WatchlistRepository watchlist;
    private ResearchRadarService service;

    @BeforeEach
    void setUp() {
        news = mock(NewsFeedService.class); repository = mock(RadarRepository.class);
        watchlist = mock(WatchlistRepository.class);
        service = new ResearchRadarService(news, repository,
                new RadarClusteringService(new RadarTextAnalyzer(new FingerprintService())),
                new RadarPriorityService(), watchlist,
                Clock.fixed(Instant.parse("2026-07-31T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void refreshesSignalsClustersAndBuildsBeginnerFriendlyCards() {
        NewsFeedItem first = item("CLS:1", "CLS", "财联社", "宁德时代发布新一代电池", NOW.minusMinutes(30));
        NewsFeedItem second = item("THS:2", "THS", "同花顺", "宁德时代新电池正式发布", NOW.minusMinutes(20));
        when(news.load("ALL", 100)).thenReturn(new NewsFeedSnapshot(Arrays.asList(first, second),
                Collections.<String>emptyList(), NOW, 2));
        AtomicLong ids = new AtomicLong();
        when(repository.capture(any(RadarSignal.class), eq(NOW))).thenAnswer(invocation -> {
            RadarSignal signal = invocation.getArgument(0); signal.setId(ids.incrementAndGet()); return signal;
        });
        when(repository.findActiveSignals(NOW.minusHours(48), 500)).thenAnswer(invocation -> Arrays.asList(
                captured(1L, first), captured(2L, second)));
        when(repository.saveEvent(any(RadarEvent.class))).thenAnswer(invocation -> {
            RadarEvent event = invocation.getArgument(0); event.setId(10L); return event;
        });
        when(watchlist.findByTypes(Arrays.asList("STOCK", "FUND"))).thenReturn(Collections.emptyList());

        ResearchRadarView view = service.load("ALL", false, 20);

        assertEquals(1, view.getEvents().size());
        assertEquals(2, view.getEvents().get(0).getSourceCount());
        assertEquals("未发现与当前自选标的的直接关系", view.getEvents().get(0).getWatchlistExplanation());
        assertEquals(2, view.getLiveItems().size());
        assertEquals(1, view.getOverview().getEventCount());
    }

    @Test
    void returnsPersistedEventsWithWarningWhenNewsFails() {
        when(news.load("ALL", 100)).thenThrow(new IllegalStateException("upstream unavailable"));
        RadarEvent saved = new RadarEvent(); saved.setId(9L); saved.setCanonicalTitle("已有事件");
        saved.setSummary("最近一次成功结果"); saved.setPriorityScore(60); saved.setSourceCount(2); saved.setSignalCount(2);
        saved.setScoreExplanation("多个来源确认；近期新信息");
        saved.setWatchlistExplanation("未发现与当前自选标的的直接关系"); saved.setLastSeenAt(NOW.minusMinutes(5));
        when(repository.findRanked("ALL", false, 20)).thenReturn(Collections.singletonList(saved));

        ResearchRadarView view = service.load("ALL", false, 20);

        assertEquals(1, view.getEvents().size());
        assertTrue(view.getWarnings().get(0).contains("最近一次"));
        assertTrue(view.getLiveItems().isEmpty());
    }

    @Test
    void busyRefreshKeepsTheLastSuccessfulLiveItems() throws Exception {
        NewsFeedItem item = item("CLS:1", "CLS", "财联社", "已缓存的实时资讯", NOW.minusMinutes(5));
        NewsFeedSnapshot snapshot = new NewsFeedSnapshot(Collections.singletonList(item), Collections.emptyList(), NOW, 1);
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        when(news.load("ALL",100)).thenReturn(snapshot).thenAnswer(invocation -> {
            entered.countDown(); release.await(3, TimeUnit.SECONDS); return snapshot;
        });
        when(repository.findActiveSignals(NOW.minusHours(48),500)).thenReturn(Collections.emptyList());
        when(watchlist.findByTypes(Arrays.asList("STOCK","FUND"))).thenReturn(Collections.emptyList());
        service.load("ALL",false,20);
        Thread refreshing = new Thread(() -> service.load("ALL",false,20)); refreshing.start();
        assertTrue(entered.await(1,TimeUnit.SECONDS));

        ResearchRadarView busy = service.load("ALL",false,20);
        release.countDown(); refreshing.join(3_000);

        assertEquals(1,busy.getLiveItems().size());
        assertEquals("已缓存的实时资讯",busy.getLiveItems().get(0).getTitle());
        assertTrue(busy.getWarnings().get(0).contains("正在刷新"));
    }

    @Test
    void preservesCategoryValidationErrors() {
        when(news.load("UNKNOWN", 100)).thenThrow(
                new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "未知或已停用的资讯分类：UNKNOWN"));
        assertThrows(BusinessException.class, () -> service.load("UNKNOWN", false, 20));
    }

    @Test
    void schedulesHighPriorityEnhancementAndReturnsEvidenceWithSanitizedTrace() {
        RadarEventEnhancementScheduler scheduler = mock(RadarEventEnhancementScheduler.class);
        RadarEvidenceRepository evidence = mock(RadarEvidenceRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        service = new ResearchRadarService(news, repository,
                new RadarClusteringService(new RadarTextAnalyzer(new FingerprintService())),
                new RadarPriorityService(), watchlist, scheduler, evidence, runs,
                Clock.fixed(Instant.parse("2026-07-31T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
        NewsFeedItem first = item("CLS:1", "CLS", "财联社", "宁德时代发布新一代电池", NOW.minusMinutes(30));
        NewsFeedItem second = item("THS:2", "THS", "同花顺", "宁德时代新电池正式发布", NOW.minusMinutes(20));
        when(news.load("ALL",100)).thenReturn(new NewsFeedSnapshot(Arrays.asList(first,second),Collections.emptyList(),NOW,2));
        when(repository.findActiveSignals(NOW.minusHours(48),500)).thenReturn(Arrays.asList(captured(1L,first),captured(2L,second)));
        when(repository.saveEvent(any(RadarEvent.class))).thenAnswer(invocation->{RadarEvent value=invocation.getArgument(0);value.setId(10L);return value;});
        com.finscope.domain.instrument.WatchlistItem followed = new com.finscope.domain.instrument.WatchlistItem();
        followed.setName("宁德时代"); followed.setCode("300750"); followed.setType("STOCK");
        when(watchlist.findByTypes(Arrays.asList("STOCK","FUND"))).thenReturn(Collections.singletonList(followed));
        ResearchRadarView view = service.load("ALL",false,20);

        verify(scheduler).schedule(any(RadarEvent.class),any(),eq(NOW),eq(true));
        assertEquals(1,view.getEvents().size());

        RadarEvent stored=new RadarEvent();stored.setId(10L);stored.setCanonicalTitle("宁德时代发布新一代电池");stored.setEvidenceStatus("SUCCESS");
        when(repository.findEvent(10L)).thenReturn(java.util.Optional.of(stored));
        when(repository.findSignalsByEventId(10L)).thenReturn(Collections.emptyList());
        when(repository.findEventSignals(10L)).thenReturn(Collections.emptyList());
        RadarEvidence external=new RadarEvidence();external.setTitle("公司公告");external.setSourceName("深交所");
        when(evidence.findByEventId(10L)).thenReturn(Collections.singletonList(external));
        AgentRun trace=new AgentRun();trace.setNodeName("radar-evidence-plan");trace.setStatus("SUCCESS");trace.setInput("完整提示词不应返回");trace.setOutput("actions=2");
        when(runs.findBySubject("RADAR_EVENT",10L)).thenReturn(Collections.singletonList(trace));

        ResearchRadarView.EventDetail detail=service.detail(10L);
        assertEquals(1,detail.getEvidence().size());
        assertEquals("radar-evidence-plan",detail.getAgentTrace().get(0).getNodeName());
        assertEquals("actions=2",detail.getAgentTrace().get(0).getSummary());
    }

    private NewsFeedItem item(String id, String provider, String source, String title, LocalDateTime time) {
        return new NewsFeedItem(id, "FLASH", title, title, "https://example.com/" + id, time,
                provider, source, "TIER_1", "COMPANY", "公司", 0.95, "测试");
    }

    private RadarSignal captured(Long id, NewsFeedItem item) {
        RadarSignal signal = new RadarSignal(); signal.setId(id); signal.setItemId(item.getId());
        signal.setProviderCode(item.getProviderCode()); signal.setSourceName(item.getSourceName()); signal.setSourceTier(item.getSourceTier());
        signal.setTitle(item.getTitle()); signal.setContent(item.getContent()); signal.setUrl(item.getUrl());
        signal.setCategoryCode(item.getCategoryCode()); signal.setPublishedAt(item.getPublishedAt());
        signal.setFirstSeenAt(item.getPublishedAt()); signal.setLastSeenAt(item.getPublishedAt()); signal.setStatus("ACTIVE");
        return signal;
    }
}
