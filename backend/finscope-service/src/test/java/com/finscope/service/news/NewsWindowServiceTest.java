package com.finscope.service.news;

import com.finscope.dao.news.NewsReportRepository;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.domain.news.*;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NewsWindowServiceTest {
    private final LocalDateTime now = LocalDateTime.parse("2026-09-21T10:00:00");

    @Test
    void productionConsumesEveryBatchWithoutTheDisplayLimit() {
        NewsReportRepository repository = mock(NewsReportRepository.class);
        NewsWindowService service = service(repository);
        List<NewsReport> first = new ArrayList<>();
        for (int i = 1; i <= 250; i++) {
            NewsReport value = new NewsReport();
            value.setId("CLS:" + i);
            value.setArrivalSequence(i);
            value.setTitle("新闻" + i);
            first.add(value);
        }
        NewsReport last = new NewsReport();
        last.setId("CLS:251");
        last.setArrivalSequence(251);
        when(repository.scan(any(), any(), eq(0L), eq(250))).thenReturn(first);
        when(repository.scan(any(), any(), eq(250L), eq(250))).thenReturn(List.of(last));
        when(repository.scan(any(), any(), eq(251L), eq(250))).thenReturn(List.of());
        assertEquals(251, service.productionSnapshot().getItems().size());
        verify(repository).scan(now.minusHours(36), now, 251L, 250);
    }

    @Test
    void unavailableCacheDoesNotBlockPersistentNewsAndPaginationTimeIsFrozen() {
        NewsReportRepository repository = mock(NewsReportRepository.class);
        NewsWindowService service = service(repository);
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        ReflectionTestUtils.setField(service, "gateway", gateway);
        when(gateway.readNewsFlashSources(any())).thenThrow(new IllegalStateException("cache unavailable"));
        when(repository.latestSequence()).thenReturn(800L);
        when(repository.query(any(), any(), any())).thenReturn(new NewsWindowPage());
        NewsWindowQuery query = new NewsWindowQuery();
        query.setAsOfTime("2026-09-21T09:00:00");
        var page = service.query(query);
        assertEquals("2026-09-21T09:00", page.getAsOfTime());
        assertTrue(page.getSourceHealth().isEmpty());
        verify(repository).query(query, now.minusHours(37), now.minusHours(1));
    }

    @Test
    void invalidWindowAndFuturePaginationAreRejected() {
        NewsWindowService service = service(mock(NewsReportRepository.class));
        NewsWindowQuery query = new NewsWindowQuery();
        query.setHours(1000);
        assertThrows(BusinessException.class, () -> service.query(query));
        query.setHours(36);
        query.setAsOfTime(now.plusDays(1).toString());
        assertThrows(BusinessException.class, () -> service.query(query));
    }

    @Test
    void sourceWithoutExternalIdUsesStableUrlAcrossTitleRevisions() {
        NewsReportRepository repository = mock(NewsReportRepository.class);
        NewsWindowService service = service(repository);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        when(categories.findEnabled()).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "categories", categories);
        ReflectionTestUtils.setField(service, "rules", new NewsRuleClassifier());
        List<NewsReport> received = new ArrayList<>();
        doAnswer(invocation -> {
            received.addAll(invocation.getArgument(0));
            return null;
        }).when(repository).ingest(anyList());
        ResearchMaterial material = new ResearchMaterial();
        material.setProviderCode("CLS");
        material.setUrl("https://example.com/announcement");
        material.setTitle("合同尚待签署");
        material.setContent("原文");
        material.setPublishedAt(now.minusMinutes(1));
        service.ingest(List.of(material));
        material.setTitle("合同已签署");
        service.ingest(List.of(material));
        assertEquals(received.get(0).getId(), received.get(1).getId());
        assertNotEquals(received.get(0).getTitle(), received.get(1).getTitle());
    }

    private NewsWindowService service(NewsReportRepository repository) {
        NewsWindowService service = new NewsWindowService();
        ReflectionTestUtils.setField(service, "reports", repository);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        return service;
    }
}
