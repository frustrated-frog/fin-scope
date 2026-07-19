package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.dao.financials.BrokerResearchReportRepository;
import com.finscope.domain.financials.BrokerResearchAnalysisResult;
import com.finscope.domain.financials.BrokerResearchReport;
import com.finscope.domain.financials.BrokerResearchReportView;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.rpc.llm.LlmChatClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerResearchServiceTest {
    @TempDir Path tempDir;

    @Test
    void storesValidPdfRunsFallbackAnalysisAndReturnsDetailedView() throws Exception {
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findByHash(any())).thenReturn(Optional.empty());
        when(repository.save(any(BrokerResearchReport.class), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    BrokerResearchReport value = invocation.getArgument(0);
                    value.setId(12L);
                    return value;
                });
        FinancialQueryService query = mock(FinancialQueryService.class);
        when(query.listReports(7L)).thenReturn(Collections.emptyList());
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        BrokerResearchAnalyzer analyzer = new BrokerResearchAnalyzer(disabledLlm(), json);
        BrokerResearchService service = new BrokerResearchService(repository,
                new BrokerResearchDocumentParser(), analyzer, new BrokerResearchFinancialLinker(),
                query, json, tempDir);
        byte[] pdf = onePagePdf();

        BrokerResearchReportView result = service.upload(7L, null, "公司深度研报", "测试证券",
                "张三", LocalDate.of(2026, 4, 20), "买入", "DEEP_DIVE", null,
                "research.pdf", new ByteArrayInputStream(pdf), pdf.length);

        assertEquals(12L, result.getReport().getId());
        assertEquals("DETERMINISTIC_FALLBACK", result.getReport().getAnalysisStatus());
        assertTrue(Files.exists(service.contentPath(12L, result.getReport())));
        assertTrue(result.getAnalysis().getLimitations().get(0).contains("模型"));
    }

    @Test
    void rejectsNonPdfBeforeWritingAnything() {
        BrokerResearchService service = new BrokerResearchService(
                mock(BrokerResearchReportRepository.class), new BrokerResearchDocumentParser(),
                new BrokerResearchAnalyzer(disabledLlm(), new ObjectMapper()),
                new BrokerResearchFinancialLinker(), mock(FinancialQueryService.class),
                new ObjectMapper(), tempDir);

        assertThrows(BusinessException.class, () -> service.upload(7L, null, null, null,
                null, null, null, null, null, "fake.pdf",
                new ByteArrayInputStream("not a pdf".getBytes()), 9));
    }

    @Test
    void rejectsCorruptedFileEvenWhenItHasAPdfHeader() {
        BrokerResearchService service = new BrokerResearchService(
                mock(BrokerResearchReportRepository.class), new BrokerResearchDocumentParser(),
                new BrokerResearchAnalyzer(disabledLlm(), new ObjectMapper()),
                new BrokerResearchFinancialLinker(), mock(FinancialQueryService.class),
                new ObjectMapper(), tempDir);
        byte[] corrupt = "%PDF-1.7\ncorrupted".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(BusinessException.class, () -> service.upload(7L, null, null, null,
                null, null, null, null, null, "corrupt.pdf",
                new ByteArrayInputStream(corrupt), corrupt.length));
    }

    @Test
    void rejectsDuplicatePdfThatBelongsToAnotherInstrument() throws Exception {
        BrokerResearchReport existing = new BrokerResearchReport();
        existing.setId(99L);
        existing.setInstrumentId(8L);
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findByHash(any())).thenReturn(Optional.of(existing));
        FinancialQueryService query = mock(FinancialQueryService.class);
        when(query.listReports(7L)).thenReturn(Collections.emptyList());
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        BrokerResearchService service = new BrokerResearchService(repository,
                new BrokerResearchDocumentParser(),
                new BrokerResearchAnalyzer(disabledLlm(), json),
                new BrokerResearchFinancialLinker(), query, json, tempDir);
        byte[] pdf = onePagePdf();

        BusinessException error = assertThrows(BusinessException.class, () -> service.upload(7L, null, null, null,
                null, null, null, null, null, "duplicate.pdf",
                new ByteArrayInputStream(pdf), pdf.length));
        assertTrue(error.getMessage().contains("另一家公司"));
    }

    @Test
    void serializesConcurrentUploadsOfTheSamePdfAndReusesOneRecord() throws Exception {
        AtomicReference<BrokerResearchReport> stored = new AtomicReference<BrokerResearchReport>();
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findByHash(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.findForecasts(any())).thenReturn(Collections.emptyList());
        when(repository.findClaims(any())).thenReturn(Collections.emptyList());
        when(repository.save(any(BrokerResearchReport.class), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    BrokerResearchReport value = invocation.getArgument(0);
                    value.setId(12L);
                    stored.set(value);
                    return value;
                });
        FinancialQueryService query = mock(FinancialQueryService.class);
        when(query.listReports(7L)).thenReturn(Collections.emptyList());
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        BrokerResearchService service = new BrokerResearchService(repository,
                new BrokerResearchDocumentParser(), new BrokerResearchAnalyzer(disabledLlm(), json),
                new BrokerResearchFinancialLinker(), query, json, tempDir);
        byte[] pdf = onePagePdf();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            java.util.concurrent.Callable<BrokerResearchReportView> upload = () -> {
                start.await();
                return service.upload(7L, null, null, null, null, null, null, null,
                        null, "same.pdf", new ByteArrayInputStream(pdf), pdf.length);
            };
            Future<BrokerResearchReportView> first = executor.submit(upload);
            Future<BrokerResearchReportView> second = executor.submit(upload);
            Future<BrokerResearchReportView> third = executor.submit(upload);
            start.countDown();

            assertEquals(12L, first.get().getReport().getId());
            assertEquals(12L, second.get().getReport().getId());
            assertEquals(12L, third.get().getReport().getId());
            verify(repository, times(1)).save(any(BrokerResearchReport.class), anyList(), anyList());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void validatesFinancialReportOwnershipBeforeReanalysisMutatesStoredData() {
        BrokerResearchReport report = new BrokerResearchReport();
        report.setId(12L);
        report.setInstrumentId(7L);
        report.setExtractedText("原文");
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findById(12L)).thenReturn(Optional.of(report));
        FinancialReport financialReport = new FinancialReport();
        financialReport.setInstrumentId(8L);
        FinancialReportView financialView = new FinancialReportView();
        financialView.setReport(financialReport);
        FinancialQueryService query = mock(FinancialQueryService.class);
        when(query.view(9L)).thenReturn(financialView);
        BrokerResearchAnalyzer analyzer = mock(BrokerResearchAnalyzer.class);
        BrokerResearchService service = new BrokerResearchService(repository,
                new BrokerResearchDocumentParser(), analyzer, new BrokerResearchFinancialLinker(),
                query, new ObjectMapper(), tempDir);

        assertThrows(BusinessException.class, () -> service.reanalyze(12L, 9L));

        verify(analyzer, never()).analyze(any(), any());
        verify(repository, never()).replaceAnalysis(any(), anyList(), anyList());
    }

    @Test
    void keepsExistingLlmAnalysisWhenReanalysisTemporarilyFallsBack() {
        BrokerResearchReport report = new BrokerResearchReport();
        report.setId(12L);
        report.setInstrumentId(7L);
        report.setExtractedText("公司原始研报文本");
        report.setOriginalFileName("research.pdf");
        report.setAnalysisStatus("LLM");
        report.setQualityLevel("HIGH");
        report.setAnalysisJson("{\"executiveSummary\":[\"此前成功生成的核心结论\"]," +
                "\"investmentThesis\":[\"此前成功生成的投资逻辑\"]," +
                "\"businessAnalysis\":[],\"industryAnalysis\":[],\"keyAssumptions\":[]," +
                "\"catalysts\":[],\"risks\":[],\"learningNotes\":[],\"glossary\":[]," +
                "\"limitations\":[],\"disclaimer\":\"仅供研究学习\"}");
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findById(12L)).thenReturn(Optional.of(report));
        when(repository.findForecasts(12L)).thenReturn(Collections.emptyList());
        when(repository.findClaims(12L)).thenReturn(Collections.emptyList());
        BrokerResearchAnalysisResult fallback = new BrokerResearchAnalysisResult();
        fallback.setAnalysisMode("DETERMINISTIC_FALLBACK");
        fallback.setQualityLevel("MEDIUM");
        fallback.setErrorMessage("模型响应超时（主调用 120 秒，结构修复 60 秒）");
        fallback.getAnalysis().getExecutiveSummary().add("本次降级内容");
        BrokerResearchAnalyzer analyzer = mock(BrokerResearchAnalyzer.class);
        when(analyzer.analyze("公司原始研报文本", "research.pdf")).thenReturn(fallback);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        BrokerResearchService service = new BrokerResearchService(repository,
                new BrokerResearchDocumentParser(), analyzer, new BrokerResearchFinancialLinker(),
                mock(FinancialQueryService.class), json, tempDir);

        BrokerResearchReportView result = service.reanalyze(12L, null);

        assertEquals("LLM", result.getReport().getAnalysisStatus());
        assertEquals("此前成功生成的核心结论", result.getAnalysis().getExecutiveSummary().get(0));
        assertTrue(result.getReport().getErrorMessage().contains("模型响应超时"));
        verify(repository, never()).replaceAnalysis(any(), anyList(), anyList());
    }

    @Test
    void importsRemotePdfWithSourceIdentityAndMetadata() throws Exception {
        BrokerResearchReportRepository repository = mock(BrokerResearchReportRepository.class);
        when(repository.findBySourceUrl("EASTMONEY",
                "https://pdf.dfcfw.com/pdf/H3_AP1_1.pdf")).thenReturn(Optional.empty());
        when(repository.findByHash(any())).thenReturn(Optional.empty());
        when(repository.save(any(BrokerResearchReport.class), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    BrokerResearchReport value = invocation.getArgument(0);
                    value.setId(77L);
                    return value;
                });
        FinancialQueryService query = mock(FinancialQueryService.class);
        com.finscope.domain.instrument.Instrument instrument =
                new com.finscope.domain.instrument.Instrument();
        instrument.setId(7L);
        instrument.setCode("SH.600519");
        when(query.instrument(7L)).thenReturn(instrument);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        BrokerResearchService service = new BrokerResearchService(repository,
                new BrokerResearchDocumentParser(), new BrokerResearchAnalyzer(disabledLlm(), json),
                new BrokerResearchFinancialLinker(), query, json, tempDir);
        com.finscope.domain.financials.BrokerResearchCandidate candidate =
                new com.finscope.domain.financials.BrokerResearchCandidate();
        candidate.setSourceCode("EASTMONEY");
        candidate.setExternalId("AP1");
        candidate.setSourceUrl("https://pdf.dfcfw.com/pdf/H3_AP1_1.pdf");
        candidate.setStockCode("600519");
        candidate.setTitle("公开研报");
        candidate.setInstitution("测试证券");
        candidate.setAnalyst("张三");
        candidate.setPublishedDate(LocalDate.of(2026, 7, 18));
        candidate.setRating("买入");
        byte[] pdf = onePagePdf();

        BrokerResearchReportView result =
                service.importRemote(7L, null, candidate, pdf);

        assertEquals("EASTMONEY", result.getReport().getSourceType());
        assertEquals(candidate.getSourceUrl(), result.getReport().getSourceUrl());
        assertEquals("公开研报", result.getReport().getTitle());
        assertEquals("测试证券", result.getReport().getInstitution());
    }

    private byte[] onePagePdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(output);
        }
        return output.toByteArray();
    }

    private LlmChatClient disabledLlm() {
        return new LlmChatClient() {
            @Override public boolean isConfigured() { return false; }
            @Override public String modelName() { return "disabled"; }
            @Override public String complete(String systemPrompt, String userPrompt) { return ""; }
        };
    }
}
