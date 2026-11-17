package com.finscope.service.radar;

import com.finscope.common.enums.news.*;
import com.finscope.dao.news.NewsAiRepository;
import com.finscope.domain.news.ai.*;
import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.dedupe.FingerprintService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NewsAiAggregationServiceTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-11-17T12:00:00");
    private final RadarTextAnalyzer analyzer = new RadarTextAnalyzer(new FingerprintService());
    private final RadarClusteringService clustering = new RadarClusteringService(analyzer);

    @Test
    void missingResultsUseCompleteLocalClusteringAndClearOldMarkers() {
        var repository = mock(NewsAiRepository.class);
        var signal = signal(1, "宁德时代发布新电池", "产品发布", now);
        signal.setAiDuplicate(true);
        signal.setAiRepostGroup("old");
        signal.setAiMeaningfulAt(now.minusHours(8));
        var results = service(repository).cluster(List.of(signal), now);
        assertEquals(1, results.size());
        assertFalse(signal.isAiDuplicate());
        assertNull(signal.getAiRepostGroup());
        assertNull(signal.getAiMeaningfulAt());
        assertNull(results.getFirst().getEvent().getAiAnalysis());
    }

    @Test
    void unreadableAiCacheCannotBreakLocalProduction() {
        var repository = mock(NewsAiRepository.class);
        when(repository.bindingsByHashes(anyCollection())).thenThrow(new IllegalStateException("corrupt-cache"));
        var signal = signal(1, "宁德时代发布新电池", "产品发布", now);
        assertEquals(1, service(repository).cluster(List.of(signal), now).size());
    }

    @Test
    void explicitAiEventsDoNotGetMergedAgainByLocalSimilarityRules() {
        var repository = mock(NewsAiRepository.class);
        var one = signal(1, "宁德时代发布新电池", "发布电池产品一", now.minusHours(1));
        var two = signal(2, "宁德时代新电池正式发布", "发布电池产品二", now);
        var a = binding(one, "event1", NewsAiRelation.NEW_EVENT);
        var b = binding(two, "event2", NewsAiRelation.CONFLICT);
        when(repository.bindingsByHashes(anyCollection())).thenReturn(Map.of(a.getContentKey(), a, b.getContentKey(), b));
        var results = service(repository).cluster(List.of(one, two), now);
        assertEquals(2, results.size());
        assertEquals(Set.of("event1", "event2"), new HashSet<>(results.stream().map(value -> value.getEvent().getEventKey()).toList()));
        assertEquals("TECHNOLOGY", new RadarDashboardCategoryService().classify(results.getFirst().getEvent()));
    }

    @Test
    void semanticRepostsDoNotCreateIndependentConfirmationsOrNewMeaningfulTime() {
        var repository = mock(NewsAiRepository.class);
        var one = signal(1, "珀云发布光学计算产品", "新产品计划上市", now.minusHours(6));
        var two = signal(2, "全新光子设备即将面世", "报道转述此前珀云公告", now);
        var a = binding(one, "event", NewsAiRelation.NEW_EVENT);
        var b = binding(two, "event", NewsAiRelation.DUPLICATE);
        when(repository.bindingsByHashes(anyCollection())).thenReturn(Map.of(a.getContentKey(), a, b.getContentKey(), b));
        var event = new NewsAiEvent();
        event.setEventKey("event");
        event.setAnalysis(a.getAnalysis());
        event.setLastAt(one.getPublishedAt());
        when(repository.events(any(), anyInt())).thenReturn(List.of(event));
        var results = service(repository).cluster(List.of(one, two), now);
        assertEquals(1, results.size());
        var sources = new RadarSourceIndependenceService(analyzer).analyze(results.getFirst().getSignals());
        assertEquals(1, sources.getIndependentSourceCount());
        assertEquals(one.getPublishedAt(), two.getAiMeaningfulAt());
        var newsworthiness = new RadarNewsworthinessService();
        ReflectionTestUtils.setField(newsworthiness, "properties", new NewswireRankingProperties());
        var assessed = newsworthiness.assess(sources, 50, null, now);
        assertEquals(one.getPublishedAt(), assessed.getMeaningfulChangeAt());
    }

    @Test
    void selectingALocalEventKeepsItsUnprocessedOriginalInsideOneGroup() {
        var repository = mock(NewsAiRepository.class);
        var one = signal(1, "珀云发布光学计算产品", "新产品计划上市", now.minusHours(1));
        var two = signal(2, "新型光子设备公布", "另一来源的转述", now);
        String legacyKey = clustering.cluster(List.of(one)).getFirst().getEvent().getEventKey();
        var binding = binding(two, legacyKey, NewsAiRelation.DUPLICATE);
        when(repository.bindingsByHashes(anyCollection())).thenReturn(Map.of(binding.getContentKey(), binding));
        var results = service(repository).cluster(List.of(one, two), now);
        assertEquals(1, results.size());
        assertEquals(2, results.getFirst().getSignals().size());
        assertEquals(legacyKey, results.getFirst().getEvent().getEventKey());
    }

    private NewsAiAggregationService service(NewsAiRepository repository) {
        var service = new NewsAiAggregationService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "clustering", clustering);
        return service;
    }

    private RadarSignal signal(long id, String title, String content, LocalDateTime publishedAt) {
        return RadarSignal.builder().id(id).itemId("item" + id).title(title).content(content).publishedAt(publishedAt)
                .firstSeenAt(publishedAt).sourceName("source" + id).providerCode("source" + id).categoryCode("COMPANY").build();
    }

    private NewsAiBinding binding(RadarSignal signal, String eventKey, NewsAiRelation relation) {
        var analysis = new NewsAiAnalysis();
        analysis.setCategoryCode("COMPANY");
        analysis.setDisplayCategory(NewsAiDisplayCategory.TECHNOLOGY);
        analysis.setTitle("珀云技术产品发布");
        analysis.setSummary("技术产品进展，不涉及收入或订单");
        var binding = new NewsAiBinding();
        binding.setContentKey(NewsAiContentIdentity.occurrence(signal.getTitle(), signal.getContent(), signal.getPublishedAt()));
        binding.setAnalysis(analysis);
        binding.setRelation(relation);
        binding.setEventKey(eventKey);
        return binding;
    }
}
