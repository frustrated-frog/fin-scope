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
    void preservesLongGapReportWithoutPromotingWrongDateFacts() {
        RunScopedResearchContextService contexts = mock(RunScopedResearchContextService.class);
        ResearchReportRepository reports = mock(ResearchReportRepository.class);
        ResearchSearchEvidenceRepository searches = mock(ResearchSearchEvidenceRepository.class);
        ResearchReportSynthesisAgent synthesis = mock(ResearchReportSynthesisAgent.class);
        ResearchRun run = new ResearchRun();
        run.setId(17L);
        run.setRunDate(java.time.LocalDate.of(2026, 11, 6));
        ResearchThesis thesis = new ResearchThesis();
        thesis.setId(4L);
        thesis.setSubjectType("COMPANY");
        thesis.setSubjectName("长鑫科技");
        thesis.setQuestion("长鑫科技今日大宗交易有哪些已确认事实？");
        Article article = new Article();
        article.setId(25L);
        article.setTitle("长鑫科技大宗交易");
        article.setUrl("https://disclosure.example.com/a");
        article.setBody("2026年8月6日，长鑫科技大宗交易成交额9.9亿元。");
        when(contexts.load(17L)).thenReturn(new RunScopedResearchContext(run, thesis,
                Collections.emptyList(), Collections.singletonList(article), Collections.emptyList(), Collections.emptyList()));
        when(searches.findByRunId(17L)).thenReturn(Collections.emptyList());
        when(reports.upsert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResearchReportService service = reportService(contexts, new ResearchEvidenceSelector(), new ResearchReportGenerator(),
                synthesis, reports, mock(ResearchThesisRepository.class), searches, new VaultWriter(tempDir), mock(ResearchRunOutputService.class));
        ResearchReport report = service.generate(17L);
        assertEquals("COMPLETED_WITH_GAPS", report.getStatus());
        assertEquals("LOW", report.getConfidence());
        assertEquals(0, report.getEvidenceCount());
        assertTrue(report.getCharacterCount() >= new ResearchReportGenerator().generate(thesis,
                Collections.emptyList(), EvidenceSufficiency.fromCounts(0, 0, 0, 0)).getMarkdown().length());
        assertTrue(report.getContentMarkdown().contains("任务契约与事实校验"));
        assertTrue(report.getWarningMessage().contains("事件日期"));
        org.junit.jupiter.api.Assertions.assertFalse(report.getContentMarkdown().contains("9.9亿元"));
        verify(synthesis, never()).refine(any(), anyList(), any());
    }

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
        article.setSummary("半导体设备需求增长，晶圆厂资本开支上调");
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
        ResearchReportService service = reportService(contextService, new ResearchEvidenceSelector(),
                new ResearchReportGenerator(), synthesisAgent, repository,
                thesisRepository, searchEvidenceRepository, new VaultWriter(tempDir), outputService);

        ResearchReport report = service.generate(14L);

        assertEquals(14L, report.getResearchRunId());
        assertTrue(report.getMarkdownPath().endsWith("research-reports/thesis-1/run-14.md"));
        assertTrue(Files.exists(Paths.get(report.getMarkdownPath())));
        assertTrue(report.getCharacterCount() <= 12000);
        assertTrue(report.getCharacterCount() >= ResearchReportPolicy.TARGET_REPORT_MIN_CHARACTERS);
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
        ResearchReportService service = reportService(contextService, new ResearchEvidenceSelector(),
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
                searchEvidence("SUPPORT", "长鑫科技上市后扩大研发投入", "长鑫科技融资支持先进制程研发")));
        when(repository.upsert(any(ResearchReport.class))).thenAnswer(invocation -> {
            ResearchReport value = invocation.getArgument(0);
            value.setId(9L);
            return value;
        });
        ResearchReportSynthesisAgent synthesis = mock(ResearchReportSynthesisAgent.class);
        when(synthesis.refine(any(), anyList(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        ResearchReportService service = reportService(contextService, new ResearchEvidenceSelector(),
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
    private ResearchReportService reportService(RunScopedResearchContextService contextService,
            ResearchEvidenceSelector evidenceSelector, ResearchReportGenerator generator,
            ResearchReportSynthesisAgent synthesis, ResearchReportRepository repository,
            ResearchThesisRepository theses, ResearchSearchEvidenceRepository searches,
            VaultWriter vault, ResearchRunOutputService outputs) {
        ResearchReportService service = new ResearchReportService();
        ResearchArchivedEventSource archived = mock(ResearchArchivedEventSource.class);
        when(archived.articlesFor(any())).thenAnswer(invocation -> ((RunScopedResearchContext) invocation.getArgument(0)).getArticles());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "contextService", contextService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "evidenceSelector", evidenceSelector);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reportGenerator", generator);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "synthesisAgent", synthesis);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reportRepository", repository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "thesisRepository", theses);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "searchEvidenceRepository", searches);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "vaultWriter", vault);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "outputService", outputs);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "archivedEventSource", archived);
        com.finscope.dao.research.quality.ResearchQualityRepository qualityRepository = mock(com.finscope.dao.research.quality.ResearchQualityRepository.class);
        when(qualityRepository.initializeContract(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(qualityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        com.finscope.service.research.quality.ResearchQualityEngine engine = new com.finscope.service.research.quality.ResearchQualityEngine();
        org.springframework.test.util.ReflectionTestUtils.setField(engine, "verifier", new com.finscope.service.research.quality.ResearchFactVerifier());
        org.springframework.test.util.ReflectionTestUtils.setField(engine, "lineage", new com.finscope.service.research.quality.ResearchSourceLineage());
        com.finscope.service.research.quality.ResearchQualityService quality = new com.finscope.service.research.quality.ResearchQualityService();
        org.springframework.test.util.ReflectionTestUtils.setField(quality, "repository", qualityRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(quality, "contractBuilder", new com.finscope.service.research.quality.ResearchTaskContractBuilder());
        org.springframework.test.util.ReflectionTestUtils.setField(quality, "engine", engine);
        org.springframework.test.util.ReflectionTestUtils.setField(quality, "candidateAgent", mock(com.finscope.service.research.quality.ResearchFactCandidateAgent.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "qualityService", quality);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "qualityRenderer", new com.finscope.service.research.quality.ResearchQualityReportRenderer());
        return service;
    }

}
