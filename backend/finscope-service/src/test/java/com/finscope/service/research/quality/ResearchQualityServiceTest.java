package com.finscope.service.research.quality;

import com.finscope.dao.research.quality.ResearchQualityRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.domain.research.quality.ResearchQualityAssessment;
import com.finscope.domain.research.quality.ResearchTaskContract;
import com.finscope.service.research.report.ResearchArchivedEventSource;
import com.finscope.service.research.report.EvidenceSufficiency;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResearchQualityServiceTest {
    @Test
    void anchorsQuotedTodayToArchivedEventAndFreezesContractOnResume() {
        ResearchQualityRepository repository = mock(ResearchQualityRepository.class);
        ResearchArchivedEventSource archive = mock(ResearchArchivedEventSource.class);
        Article archived = new Article();
        archived.setUrl("https://original.cn/event");
        archived.setPublishedAt(LocalDate.of(2026, 8, 6).atStartOfDay());
        when(archive.archivedArticlesFor(any(), any())).thenReturn(List.of(archived));
        when(repository.initializeContract(any())).thenAnswer(call -> call.getArgument(0));
        ResearchQualityService service = service(repository, archive, mock(ResearchFactCandidateAgent.class));
        ResearchRun run = run();
        ResearchThesis thesis = thesis();
        thesis.setQuestion("围绕“长鑫科技今日大宗交易”，哪些事实已经确认，后续应重点观察什么？");
        ResearchTaskContract frozen = service.initialize(run, thesis);
        assertEquals("2026-08-06", frozen.getDateFrom());
        assertEquals("https://original.cn/event", frozen.getDateAnchorUrl());
        when(repository.findContract(1L)).thenReturn(Optional.of(frozen));
        thesis.setQuestion("更改后的问题");
        assertEquals(frozen.getQuestion(), service.taskThesis(run, thesis).getQuestion());
        assertEquals("更改后的问题", thesis.getQuestion());
        assertSame(frozen, service.initialize(run, thesis));
        verify(archive, times(1)).archivedArticlesFor(any(), any());
    }

    @Test
    void repeatedAuditReusesSnapshotAndDoesNotConsumeAnotherModelAttempt() {
        ResearchQualityRepository repository = mock(ResearchQualityRepository.class);
        ResearchArchivedEventSource archive = mock(ResearchArchivedEventSource.class);
        when(archive.archivedArticlesFor(any(), any())).thenReturn(Collections.emptyList());
        ResearchFactCandidateAgent agent = mock(ResearchFactCandidateAgent.class);
        when(agent.isConfigured()).thenReturn(true);
        when(repository.reserveModelAttempt(1L)).thenReturn(true);
        when(repository.initializeContract(any())).thenAnswer(call -> call.getArgument(0));
        Map<String, ResearchQualityAssessment> cache = new HashMap<>();
        when(repository.find(any(), anyString())).thenAnswer(call -> Optional.ofNullable(cache.get(call.getArgument(1))));
        when(repository.save(any())).thenAnswer(call -> {
            ResearchQualityAssessment value = call.getArgument(0);
            cache.put(value.getFingerprint(), value);
            return value;
        });
        ResearchQualityService service = service(repository, archive, agent);
        ResearchQualityAssessment first = service.assess(run(), thesis(), List.of(), List.of(), List.of(), true);
        ResearchQualityAssessment second = service.assess(run(), thesis(), List.of(), List.of(), List.of(), true);
        assertSame(first, second);
        verify(repository, times(1)).reserveModelAttempt(1L);
        verify(agent, times(1)).propose(any(), anyList());
    }

    @Test
    void rejectedIrrelevantSourcesDoNotBlockAnOtherwiseCompleteTask() {
        ResearchQualityAssessment quality = new ResearchQualityAssessment();
        quality.setIndependentSourceCount(3);
        quality.getFeedback().add("有一个不相关网页已拒绝");
        EvidenceSufficiency result = EvidenceSufficiency.fromCounts(7, 3, 4, 3).withQuality(quality);
        assertTrue(result.isSufficient());
        assertTrue(result.getWarnings().isEmpty());
    }

    private ResearchQualityService service(ResearchQualityRepository repository, ResearchArchivedEventSource archive,
                                           ResearchFactCandidateAgent agent) {
        ResearchQualityService service = new ResearchQualityService();
        ResearchQualityEngine engine = new ResearchQualityEngine();
        ReflectionTestUtils.setField(engine, "verifier", new ResearchFactVerifier());
        ReflectionTestUtils.setField(engine, "lineage", new ResearchSourceLineage());
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "contractBuilder", new ResearchTaskContractBuilder());
        ReflectionTestUtils.setField(service, "archivedEventSource", archive);
        ReflectionTestUtils.setField(service, "engine", engine);
        ReflectionTestUtils.setField(service, "candidateAgent", agent);
        return service;
    }

    private ResearchRun run() {
        ResearchRun run = new ResearchRun();
        run.setId(1L);
        run.setRunDate(LocalDate.of(2026, 11, 6));
        return run;
    }

    private ResearchThesis thesis() {
        ResearchThesis thesis = new ResearchThesis();
        thesis.setSubjectName("长鑫科技");
        thesis.setQuestion("长鑫科技大宗交易有哪些事实？");
        return thesis;
    }
}
