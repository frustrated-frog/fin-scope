package com.finscope.service.research.report;

import com.finscope.dao.research.ResearchReportRepository;
import com.finscope.dao.research.ResearchThesisRepository;
import com.finscope.domain.article.Article;
import com.finscope.domain.research.ResearchReport;
import com.finscope.domain.research.ResearchRun;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        when(repository.upsert(any(ResearchReport.class))).thenAnswer(invocation -> {
            ResearchReport report = invocation.getArgument(0);
            report.setId(7L);
            return report;
        });
        ResearchReportService service = new ResearchReportService(contextService, new ResearchEvidenceSelector(),
                new ResearchReportGenerator(), new ResearchReportSynthesisAgent(), repository,
                thesisRepository, new VaultWriter(tempDir), outputService);

        ResearchReport report = service.generate(14L);

        assertEquals(14L, report.getResearchRunId());
        assertTrue(report.getMarkdownPath().endsWith("research-reports/thesis-1/run-14.md"));
        assertTrue(Files.exists(Paths.get(report.getMarkdownPath())));
        assertTrue(report.getCharacterCount() <= 12000);
        verify(outputService).record(14L, ResearchRunOutputService.REPORT, 7L);
        verify(thesisRepository).update(thesis);
    }
}
