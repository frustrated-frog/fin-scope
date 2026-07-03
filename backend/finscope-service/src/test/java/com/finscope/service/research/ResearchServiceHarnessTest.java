package com.finscope.service.research;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.article.ArticleRepository;
import com.finscope.dao.research.ContentIdeaRepository;
import com.finscope.dao.research.EventClusterRepository;
import com.finscope.dao.research.EvidenceItemRepository;
import com.finscope.dao.research.LearningTaskRepository;
import com.finscope.dao.research.ResearchRunRepository;
import com.finscope.dao.source.SourceRepository;
import com.finscope.domain.agent.AgentNodeResult;
import com.finscope.domain.brief.Brief;
import com.finscope.domain.fetch.FetchRun;
import com.finscope.domain.research.ResearchEnums;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchRunPlan;
import com.finscope.domain.research.SourceProfile;
import com.finscope.domain.research.ThemeProfile;
import com.finscope.service.agent.ActionFingerprintService;
import com.finscope.service.agent.AgentHarness;
import com.finscope.service.agent.AgentTraceService;
import com.finscope.service.brief.BriefService;
import com.finscope.service.fetch.FetchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchServiceHarnessTest {
    @Test
    void skipsRepeatedSourceFetchAtHardThresholdAndRecordsTrace() {
        ResearchService service = new ResearchService();
        ThemeProfileService themeProfileService = mock(ThemeProfileService.class);
        SourcePlanner sourcePlanner = mock(SourcePlanner.class);
        SourceRepository sourceRepository = mock(SourceRepository.class);
        ResearchRunRepository researchRunRepository = mock(ResearchRunRepository.class);
        FetchService fetchService = mock(FetchService.class);
        BriefService briefService = mock(BriefService.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EventClusterRepository eventClusterRepository = mock(EventClusterRepository.class);
        EvidenceItemRepository evidenceItemRepository = mock(EvidenceItemRepository.class);
        LearningTaskRepository learningTaskRepository = mock(LearningTaskRepository.class);
        ContentIdeaRepository contentIdeaRepository = mock(ContentIdeaRepository.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);

        ReflectionTestUtils.setField(service, "themeProfileService", themeProfileService);
        ReflectionTestUtils.setField(service, "sourcePlanner", sourcePlanner);
        ReflectionTestUtils.setField(service, "sourceRepository", sourceRepository);
        ReflectionTestUtils.setField(service, "researchRunRepository", researchRunRepository);
        ReflectionTestUtils.setField(service, "fetchService", fetchService);
        ReflectionTestUtils.setField(service, "briefService", briefService);
        ReflectionTestUtils.setField(service, "articleRepository", articleRepository);
        ReflectionTestUtils.setField(service, "eventClusterRepository", eventClusterRepository);
        ReflectionTestUtils.setField(service, "evidenceItemRepository", evidenceItemRepository);
        ReflectionTestUtils.setField(service, "learningTaskRepository", learningTaskRepository);
        ReflectionTestUtils.setField(service, "contentIdeaRepository", contentIdeaRepository);
        ReflectionTestUtils.setField(service, "agentRunRepository", mock(AgentRunRepository.class));
        ReflectionTestUtils.setField(service, "agentHarness", new AgentHarness());
        ReflectionTestUtils.setField(service, "actionFingerprintService", new ActionFingerprintService());
        ReflectionTestUtils.setField(service, "agentTraceService", agentTraceService);

        LocalDate runDate = LocalDate.of(2026, 7, 3);
        when(themeProfileService.getRequired(anyList())).thenReturn(Collections.singletonList(theme()));
        when(sourceRepository.findAll()).thenReturn(Collections.emptyList());
        when(sourcePlanner.plan(any(LocalDate.class), anyList(), anyInt(), anyBoolean(), anyList()))
                .thenReturn(Arrays.asList(source(12L), source(12L), source(12L)));
        when(researchRunRepository.save(any(ResearchRun.class))).thenAnswer(invocation -> {
            ResearchRun run = invocation.getArgument(0);
            run.setId(501L);
            return run;
        });
        when(researchRunRepository.updateResult(any(ResearchRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fetchService.fetch(12L)).thenReturn(fetchRun());
        when(briefService.generate(runDate)).thenReturn(brief(runDate));

        ResearchRunPlan plan = service.createRun(runDate,
                Collections.singletonList(ResearchEnums.THEME_MARKET), 3, true);

        verify(fetchService, times(2)).fetch(12L);
        ArgumentCaptor<AgentNodeResult> resultCaptor = ArgumentCaptor.forClass(AgentNodeResult.class);
        verify(agentTraceService, times(3)).recordNode(isNull(), isNull(), any(), any(),
                resultCaptor.capture(), anyLong(), any());
        AgentNodeResult skipped = resultCaptor.getAllValues().get(2);
        assertEquals("SKIPPED", skipped.getStatus());
        assertEquals("REPEATED_ACTION", skipped.getErrorType());
        assertEquals(ResearchEnums.RUN_STATUS_PARTIAL_SUCCESS, plan.getRun().getStatus());
        assertTrue(plan.getRun().getErrorMessage().contains("Repeated action reached hard threshold"));
    }

    private ThemeProfile theme() {
        ThemeProfile theme = new ThemeProfile();
        theme.setCode(ResearchEnums.THEME_MARKET);
        theme.setName("市场");
        return theme;
    }

    private SourceProfile source(Long sourceId) {
        SourceProfile source = new SourceProfile();
        source.setSourceId(sourceId);
        source.setSourceName("Source " + sourceId);
        source.setSourceTier(ResearchEnums.SOURCE_TIER_MEDIA);
        source.setThemeCodes(Collections.singletonList(ResearchEnums.THEME_MARKET));
        source.setCredibility(4);
        source.setEnabled(true);
        return source;
    }

    private FetchRun fetchRun() {
        FetchRun run = new FetchRun();
        run.setSourceId(12L);
        run.setSourceName("Source 12");
        run.setStatus("SUCCESS");
        run.setSuccessCount(2);
        run.setDuplicateCount(1);
        return run;
    }

    private Brief brief(LocalDate date) {
        Brief brief = new Brief();
        brief.setBriefDate(date);
        brief.setTitle("Daily Brief");
        brief.setContent("content");
        brief.setMarkdownPath("daily.md");
        return brief;
    }
}
