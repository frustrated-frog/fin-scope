package com.finscope.service.research.agent.tool;

import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.research.agent.ResearchToolObservation;
import com.finscope.service.fetch.FetchService;
import com.finscope.service.research.ResearchRunOutputService;
import com.finscope.service.research.mission.ResearchSearchSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicNewsSearchToolTest {
    private FetchService fetchService;
    private ResearchRunOutputService outputs;
    private PublicNewsSearchTool tool;

    @BeforeEach
    void setUp() {
        fetchService = mock(FetchService.class);
        outputs = mock(ResearchRunOutputService.class);
        tool = new PublicNewsSearchTool(fetchService, outputs, new ResearchSearchSourceFactory());
    }

    @Test
    void returnsSuccessfulObservationWithEvidenceAndSourceDeltas() {
        when(outputs.count(22L, ResearchRunOutputService.EVIDENCE)).thenReturn(4, 5);
        when(outputs.countDistinctArticleSources(22L)).thenReturn(2, 3);
        when(fetchService.fetch(any(com.finscope.domain.source.Source.class))).thenReturn(fetch("SUCCESS", null));

        ResearchToolObservation observation = tool.execute(new ResearchAgentToolContext(22L, 9L), arguments());

        assertEquals("SUCCESS", observation.getStatus());
        assertEquals(1, observation.getEvidenceDelta());
        assertEquals(1, observation.getSourceDelta());
        assertTrue(observation.getObservationSummary().contains("新增证据=1"));
        assertFalse(observation.isRetryable());
    }

    @Test
    void distinguishesNoProgressFromRetryableFetchFailure() {
        when(outputs.count(22L, ResearchRunOutputService.EVIDENCE)).thenReturn(4, 4, 4, 4);
        when(outputs.countDistinctArticleSources(22L)).thenReturn(2, 2, 2, 2);
        when(fetchService.fetch(any(com.finscope.domain.source.Source.class)))
                .thenReturn(fetch("SUCCESS", null), fetch("FAILED", "network timeout"));

        ResearchToolObservation noProgress = tool.execute(new ResearchAgentToolContext(22L, 9L), arguments());
        ResearchToolObservation failed = tool.execute(new ResearchAgentToolContext(22L, 10L), arguments());

        assertEquals("NO_PROGRESS", noProgress.getStatus());
        assertEquals("RETRYABLE_ERROR", failed.getStatus());
        assertEquals("SEARCH_FETCH_FAILED", failed.getErrorType());
        assertTrue(failed.isRetryable());
    }

    private Map<String, Object> arguments() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("query", "光模块 指引 下修 风险");
        value.put("intent", "COUNTER");
        return value;
    }

    private FetchRun fetch(String status, String error) {
        FetchRun value = new FetchRun();
        value.setId(301L);
        value.setSourceName("Google News · 反方证据搜索");
        value.setStatus(status);
        value.setSuccessCount("SUCCESS".equals(status) ? 2 : 0);
        value.setDuplicateCount(0);
        value.setErrorMessage(error);
        return value;
    }
}
