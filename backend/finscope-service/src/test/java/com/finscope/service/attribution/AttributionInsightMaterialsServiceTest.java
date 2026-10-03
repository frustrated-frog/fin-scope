package com.finscope.service.attribution;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.domain.attribution.*;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.search.evidence.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttributionInsightMaterialsServiceTest {
    @Test
    void preservesOldBusinessBackgroundButExcludesFuturePublicationsAndBoundsSupplement() {
        var service = new AttributionInsightMaterialsService();
        var gateway = mock(SearchEvidenceGateway.class);
        var content = mock(SearchEvidenceContentService.class);
        ReflectionTestUtils.setField(service, "searchEvidenceGateway", gateway);
        ReflectionTestUtils.setField(service, "searchEvidenceContentService", content);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        when(gateway.isConfigured(SearchDepth.DEEP)).thenReturn(true);
        var hits = new ArrayList<SearchEvidence>();
        for (int i = 0; i < 5; i++) {
            var hit = new SearchEvidence();
            hit.setTitle("业务材料" + i);
            hit.setUrl("https://example.com/business/" + i);
            hit.setContent("公司主要生产光纤");
            hit.setPublishedAt("2026-08-01");
            hits.add(hit);
        }
        hits.get(0).setPublishedAt("2026-09-18T18:00:00Z"); // 中国时间已到次日
        hits.get(1).setUrl("javascript:alert(1)");
        when(gateway.search(any())).thenReturn(new SearchEvidenceBatch(hits, List.of(), false));
        when(content.acquire(any(), anyString(), anyString(), eq(true))).thenThrow(new IllegalStateException("unavailable"));
        var report = new AttributionReport();
        report.setReportDate(LocalDate.parse("2026-09-18"));
        var stock = new Instrument();
        stock.setCode("603618");
        stock.setName("杭电股份");
        var original = new AttributionEvidence();
        original.setTitle("上年度年报");
        original.setUrl("https://example.com/annual");
        original.setPublishedAt("2026-04-01");
        original.setSnippet("本次主营业务披露");
        var result = new AttributionResearchInsights();
        service.collect(result, report, stock, List.of(original));
        assertEquals(3, result.getSources().size());
        assertEquals("上年度年报", result.getSources().get(0).getTitle());
        assertFalse(original.isHistoricalContext());
        assertTrue(result.getSources().stream().noneMatch(source -> source.getTitle().equals("业务材料0")));
        verify(content, times(2)).acquire(any(), anyString(), anyString(), eq(true));
        var queries = ArgumentCaptor.forClass(SearchEvidenceRequest.class);
        verify(gateway, times(2)).search(queries.capture());
        assertTrue(queries.getValue().getQuery().contains("before:2026-09-19"));
        assertFalse(result.getWarnings().isEmpty());
    }
}
