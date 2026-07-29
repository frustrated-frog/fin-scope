package com.finscope.service.research.report;

import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.dao.research.ResearchSearchEvidenceRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRun;
import com.finscope.domain.research.ResearchSearchEvidence;
import com.finscope.domain.research.ResearchThesis;
import com.finscope.service.research.ResearchRunOutputService;
import com.finscope.service.vault.VaultWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchReportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesPersistsAndProjectsAnIndependentRunScopedReport() throws Exception {
        RunScopedResearchContextService contextService = mock(RunScopedResearchContextService.class);
        ResearchReportRepository repository = mock(ResearchReportRepository.class);
        ResearchRunOutputService outputService = mock(ResearchRunOutputService.class);
        ResearchThesisRepository thesisRepository = mock(ResearchThesisRepository.class);
        ResearchSearchEvidenceRepository searchEvidenceRepository = mock(ResearchSearchEvidenceRepository.class);
        ResearchRun run = new ResearchRun();
        run.setId(14L);
        run.setThesisId(1L);
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(1L);
        thesis.setSubjectName("半导体设备");
        thesis.setQuestion("周期是否还能持续");
        Article article = new Article();
        article.setId(24L);
        article.setSourceName("行业媒体");
        article.setTitle("半导体设备订单增长");
        article.setSummary("晶圆厂资本开支上调");
        article.setUrl("https://example.com/24");
        when(contextService.load(14L)).thenReturn(new RunScopedResearchContext(run, thesis,
                Collections.emptyList(), Collections.singletonList(article), Collections.emptyList(), Collections.emptyList()));
        when(searchEvidenceRepository.findByRunId(14L)).thenReturn(Collections.emptyList());
        when(repository.upsert(any(ResearchReport.class))).thenAnswer(invocation -> {
            ResearchReport report = invocation.getArgument(0);
            report.setId(7L);
            return report;
        });
        ResearchReportSynthesisAgent synthesisAgent = mock(ResearchReportSynthesisAgent.class);
        when(synthesisAgent.refine(any(), anyList(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        ResearchReportService service = new ResearchReportService(contextService, new ResearchEvidenceSelector(),
                new ResearchReportGenerator(), synthesisAgent, repository,
                thesisRepository, searchEvidenceRepository, new VaultWriter(tempDir), outputService);

        ResearchReport report = service.generate(14L);

        assertEquals(14L, report.getResearchRunId());
        assertTrue(report.getMarkdownPath().endsWith("research-reports/thesis-1/run-14.md"));
        assertTrue(Files.exists(Paths.get(report.getMarkdownPath())));
        assertTrue(report.getCharacterCount() <= 12000);
        verify(outputService).record(14L, ResearchRunOutputService.REPORT, 7L);
        verify(thesisRepository).update(thesis);
    }

    @Test
    void refusesToPersistAConclusionReportWithoutSelectedEvidence() throws Exception {
        RunScopedResearchContextService contextService = mock(RunScopedResearchContextService.class);
        ResearchReportRepository repository = mock(ResearchReportRepository.class);
        ResearchRunOutputService outputService = mock(ResearchRunOutputService.class);
        ResearchThesisRepository thesisRepository = mock(ResearchThesisRepository.class);
        ResearchSearchEvidenceRepository searchEvidenceRepository = mock(ResearchSearchEvidenceRepository.class);
        ResearchRun run = new ResearchRun();
        run.setId(15L);
        run.setThesisId(2L);
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(2L);
        thesis.setSubjectName("AI算力");
        thesis.setQuestion("资本开支能否持续");
        when(contextService.load(15L)).thenReturn(new RunScopedResearchContext(run, thesis,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        when(searchEvidenceRepository.findByRunId(15L)).thenReturn(Collections.emptyList());
        VaultWriter vaultWriter = mock(VaultWriter.class);
        ResearchReportService service = new ResearchReportService(contextService, new ResearchEvidenceSelector(),
                new ResearchReportGenerator(), mock(ResearchReportSynthesisAgent.class), repository,
                thesisRepository, searchEvidenceRepository, vaultWriter, outputService);

        InsufficientResearchEvidenceException error = assertThrows(
                InsufficientResearchEvidenceException.class, () -> service.generate(15L));

        assertTrue(error.getMessage().contains("没有可引用的有效证据"));
        verify(repository, never()).upsert(any(ResearchReport.class));
        verify(vaultWriter, never()).writeResearchReport(any(), any(), any());
        verify(outputService, never()).record(any(), any(), any());
    }

    @Test
    void generatesReportFromRunScopedSearchEvidenceWithoutArticleLibraryRecords() {
        RunScopedResearchContextService contextService = mock(RunScopedResearchContextService.class);
        ResearchReportRepository repository = mock(ResearchReportRepository.class);
        ResearchRunOutputService outputService = mock(ResearchRunOutputService.class);
        ResearchThesisRepository thesisRepository = mock(ResearchThesisRepository.class);
        ResearchSearchEvidenceRepository searchEvidenceRepository = mock(ResearchSearchEvidenceRepository.class);
        ResearchRun run = new ResearchRun();
        run.setId(16L);
        run.setThesisId(3L);
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(3L);
        thesis.setSubjectType("COMPANY");
        thesis.setSubjectName("长鑫科技");
        thesis.setQuestion("长鑫科技上市带来了哪些影响？");
        when(contextService.load(16L)).thenReturn(new RunScopedResearchContext(run, thesis,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        when(searchEvidenceRepository.findByRunId(16L)).thenReturn(Collections.singletonList(
                searchEvidence("SUPPORT", "长鑫科技上市后扩大研发投入", "融资支持先进制程研发")));
        when(repository.upsert(any(ResearchReport.class))).thenAnswer(invocation -> {
            ResearchReport value = invocation.getArgument(0);
            value.setId(9L);
            return value;
        });
        ResearchReportSynthesisAgent synthesis = mock(ResearchReportSynthesisAgent.class);
        when(synthesis.refine(any(), anyList(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        ResearchReportService service = new ResearchReportService(contextService, new ResearchEvidenceSelector(),
                new ResearchReportGenerator(), synthesis, repository, thesisRepository,
                searchEvidenceRepository, new VaultWriter(tempDir), outputService);

        ResearchReport report = service.generate(16L);

        assertEquals(1, report.getEvidenceCount());
        assertEquals(1, report.getSourceCount());
    }

    private ResearchSearchEvidence searchEvidence(String intent, String title, String content) {
        ResearchSearchEvidence value = new ResearchSearchEvidence();
        value.setId(91L);
        value.setResearchRunId(16L);
        value.setProvider("TAVILY");
        value.setIntent(intent);
        value.setTitle(title);
        value.setContent(content);
        value.setUrl("https://exchange.example.com/91");
        value.setSourceDomain("exchange.example.com");
        value.setSourceTier("T1");
        value.setRelevanceScore(0.95D);
        return value;
    }
}
