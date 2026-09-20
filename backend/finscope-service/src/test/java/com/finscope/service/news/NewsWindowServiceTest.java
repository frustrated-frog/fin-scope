package com.finscope.service.news;

import com.finscope.dao.cache.EphemeralContentCacheProperties;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.dao.news.NewsWindowRepository;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.NewsItemClassification;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NewsWindowServiceTest {
    @Test
    void filtersEntireWindowBeforeCountingAndPagingWithManualCategoryPrecedence() {
        NewsWindowRepository repository = mock(NewsWindowRepository.class);
        NewsClassificationRepository classifications = mock(NewsClassificationRepository.class);
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        NewsFeedService feed = mock(NewsFeedService.class);
        when(feed.categories()).thenReturn(Arrays.asList(
                new NewsCategory("COMPANY", "公司", "", true, 1),
                new NewsCategory("INDUSTRY", "行业", "", true, 2)));
        when(feed.matches(anyString(), any())).thenCallRealMethod();
        when(feed.map(any())).thenCallRealMethod();
        when(feed.enrich(any(), any(), any())).thenCallRealMethod();
        List<String> ids = IntStream.range(0, 150).mapToObj(i -> "CLS:" + i).collect(Collectors.toList());
        when(repository.findIds(any(), any(), eq("ALL"), eq(""))).thenReturn(ids);
        Map<String, NewsItemClassification> saved = new HashMap<>();
        for (int index = 100; index < 150; index++) {
            NewsItemClassification value = new NewsItemClassification();
            value.setItemId(ids.get(index));
            value.setStatus("CLASSIFIED");
            value.setCategoryCode("COMPANY");
            value.setManualCategoryCode("INDUSTRY");
            value.setReviewStatus("CORRECTED");
            saved.put(value.getItemId(), value);
        }
        when(classifications.findByItemIds(ids)).thenReturn(saved);
        when(repository.findByIds(any())).thenAnswer(invocation -> {
            List<String> selected = invocation.getArgument(0);
            List<ResearchMaterial> result = new ArrayList<>();
            for (String id : selected) {
                ResearchMaterial material = new ResearchMaterial();
                material.setProviderCode("CLS");
                material.setExternalId(id.substring(4));
                result.add(material);
            }
            return result;
        });
        when(repository.providers(any(), any())).thenReturn(Collections.singletonList("CLS"));
        when(gateway.readNewsFlashSources(any())).thenReturn(new ResearchMaterialGatewayResult(
                Collections.emptyList(), Collections.emptyList()));
        NewsWindowService service = new NewsWindowService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "classifications", classifications);
        ReflectionTestUtils.setField(service, "feed", feed);
        ReflectionTestUtils.setField(service, "gateway", gateway);
        ReflectionTestUtils.setField(service, "properties", new EphemeralContentCacheProperties());
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC));

        NewsFeedSnapshot page = service.load("INDUSTRY", "ALL", "", 36, 1, 20, null);

        assertEquals(50, page.getTotalCount());
        assertEquals(3, page.getTotalPages());
        assertEquals(20, page.getItems().size());
        assertEquals("CLS:120", page.getItems().get(0).getId());
        assertEquals(150, page.getCategoryCounts().get("ALL"));
        assertEquals(50, page.getCategoryCounts().get("INDUSTRY"));
        assertEquals(0, page.getCategoryCounts().get("COMPANY"));
        assertEquals(100, page.getUnclassifiedCount());
        assertEquals(LocalDateTime.of(2026, 9, 21, 10, 0), page.getAsOf());
        verify(repository).findByIds(ids.subList(120, 140));
        verify(classifications).findByItemIds(ids);
        assertEquals(2, service.load("INDUSTRY", "ALL", "", 36, Integer.MAX_VALUE, 20, null).getPage());
        assertThrows(com.finscope.common.exception.BusinessException.class,
                () -> service.load("INVALID", "ALL", "", 36, 0, 50, null));
    }
}
