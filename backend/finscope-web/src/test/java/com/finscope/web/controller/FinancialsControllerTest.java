package com.finscope.web.controller;

import com.finscope.domain.financials.FinancialReport;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialDocument;
import com.finscope.domain.financials.FinancialEvidence;
import com.finscope.domain.financials.FinancialInterpretation;
import com.finscope.domain.financials.BrokerResearchAnalysis;
import com.finscope.domain.financials.BrokerResearchReport;
import com.finscope.domain.financials.BrokerResearchReportView;
import com.finscope.domain.financials.BrokerResearchCandidate;
import com.finscope.domain.financials.BrokerResearchSyncResult;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.financials.FinancialDocumentService;
import com.finscope.service.financials.FinancialQueryService;
import com.finscope.service.financials.FinancialRefreshService;
import com.finscope.service.financials.GlobalFinancialRefreshService;
import com.finscope.service.financials.FinancialInterpretationFacade;
import com.finscope.service.financials.BrokerResearchService;
import com.finscope.service.financials.BrokerResearchSyncService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({FinancialsController.class, FinancialDocumentContentController.class,
        BrokerResearchContentController.class})
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class FinancialsControllerTest {
    @TempDir
    Path tempDir;
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private FinancialQueryService query;
    @MockBean
    private FinancialRefreshService refresh;
    @MockBean
    private GlobalFinancialRefreshService globalRefresh;
    @MockBean
    private FinancialDocumentService documents;
    @MockBean
    private FinancialInterpretationFacade interpretations;
    @MockBean
    private BrokerResearchService brokerResearch;
    @MockBean
    private BrokerResearchSyncService brokerResearchSync;

    @Test
    void listsSupportedStockInstruments() throws Exception {
        Instrument stock = new Instrument();
        stock.setId(7L);
        stock.setCode("600519");
        stock.setName("贵州茅台");
        stock.setType("STOCK");
        stock.setMarket("SH");
        when(query.listInstruments()).thenReturn(Collections.singletonList(stock));

        mockMvc.perform(get("/api/financials/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("600519"));
    }

    @Test
    void refreshesRequestedReportAndReturnsOkEnvelope() throws Exception {
        FinancialReport report = new FinancialReport();
        report.setId(9L);
        report.setPeriodEnd(LocalDate.of(2026, 6, 30));
        report.setReportType(FinancialReportType.HALF_YEAR);
        FinancialReportView view = new FinancialReportView();
        view.setReport(report);
        when(refresh.refresh(eq(7L), eq(LocalDate.of(2026, 6, 30)),
                eq(FinancialReportType.HALF_YEAR))).thenReturn(view);

        mockMvc.perform(post("/api/financials/instruments/7/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodEnd\":\"2026-06-30\",\"reportType\":\"HALF_YEAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.id").value(9))
                .andExpect(jsonPath("$.data.report.reportType").value("HALF_YEAR"));
    }

    @Test
    void refreshesASecCompanyWithoutRequiringAWatchlistInstrument() throws Exception {
        FinancialReport report = new FinancialReport();
        report.setId(19L);
        report.setPeriodEnd(LocalDate.of(2025, 9, 27));
        report.setReportType(FinancialReportType.ANNUAL);
        FinancialReportView view = new FinancialReportView();
        view.setReport(report);
        when(globalRefresh.refresh(eq("SEC_EDGAR"), eq("CIK0000320193"),
                eq("Apple Inc."), eq("AAPL"), eq("Nasdaq"),
                eq(LocalDate.of(2025, 12, 31)), eq(FinancialReportType.ANNUAL)))
                .thenReturn(view);

        mockMvc.perform(post("/api/financials/global/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerCode\":\"SEC_EDGAR\","
                                + "\"providerCompanyId\":\"CIK0000320193\","
                                + "\"displayName\":\"Apple Inc.\",\"symbol\":\"AAPL\","
                                + "\"exchange\":\"Nasdaq\",\"periodEnd\":\"2025-12-31\","
                                + "\"reportType\":\"ANNUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.id").value(19))
                .andExpect(jsonPath("$.data.report.periodEnd").value("2025-09-27"));
    }

    @Test
    void uploadsPdfAndReturnsParseStatus() throws Exception {
        FinancialDocument document = new FinancialDocument();
        document.setId(11L);
        document.setInstrumentId(7L);
        document.setReportId(9L);
        document.setParseStatus("PARSED");
        document.setPageCount(1);
        when(documents.store(eq(7L), eq(9L), eq("report.pdf"), any(), anyLong()))
                .thenReturn(document);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf",
                "%PDF-1.4 sample".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/financials/documents/upload")
                        .file(file)
                        .param("instrumentId", "7")
                        .param("reportId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.parseStatus").value("PARSED"));
    }

    @Test
    void streamsPdfContentOutsideTheJsonEnvelopeContract() throws Exception {
        byte[] bytes = "%PDF-1.4 content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path path = tempDir.resolve("report.pdf");
        Files.write(path, bytes);
        FinancialDocument document = new FinancialDocument();
        document.setId(11L);
        document.setFileHash("abc123");
        when(documents.get(11L)).thenReturn(document);
        when(documents.contentPath(11L)).thenReturn(path);

        mockMvc.perform(get("/api/financials/documents/11/content"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition",
                                "inline; filename=\"abc123.pdf\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                .content().bytes(bytes));
    }

    @Test
    void exposesAsyncInterpretationLatestHistoryDetailAndEvidenceContracts() throws Exception {
        FinancialInterpretation value = new FinancialInterpretation();
        value.setId(41L);
        value.setReportId(9L);
        value.setStatus("QUEUED");
        when(interpretations.request(9L, false)).thenReturn(value);
        when(interpretations.latest(9L)).thenReturn(value);
        when(interpretations.history(9L, 20)).thenReturn(Collections.singletonList(value));
        when(interpretations.get(41L)).thenReturn(value);
        FinancialEvidence evidence = new FinancialEvidence();
        evidence.setId("M_REVENUE_YOY");
        when(interpretations.evidence(41L)).thenReturn(Collections.singletonList(evidence));

        mockMvc.perform(post("/api/financials/reports/9/interpretations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(41))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
        mockMvc.perform(get("/api/financials/reports/9/interpretations/latest"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(41));
        mockMvc.perform(get("/api/financials/reports/9/interpretations?limit=20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(41));
        mockMvc.perform(get("/api/financials/interpretations/41"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reportId").value(9));
        mockMvc.perform(get("/api/financials/interpretations/41/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("M_REVENUE_YOY"));
    }

    @Test
    void exposesBrokerResearchListDetailUploadAndReanalysisContracts() throws Exception {
        BrokerResearchReport report = new BrokerResearchReport();
        report.setId(12L);
        report.setInstrumentId(7L);
        report.setTitle("贵州茅台深度报告");
        report.setInstitution("测试证券");
        report.setRating("买入");
        report.setTargetPrice(new BigDecimal("1800"));
        report.setAnalysisStatus("LLM");
        BrokerResearchAnalysis analysis = new BrokerResearchAnalysis();
        analysis.setExecutiveSummary(Arrays.asList("品牌壁垒稳固", "产品结构持续升级"));
        BrokerResearchReportView view = new BrokerResearchReportView();
        view.setReport(report);
        view.setAnalysis(analysis);
        when(brokerResearch.list(7L)).thenReturn(Collections.singletonList(report));
        when(brokerResearch.get(12L, 9L)).thenReturn(view);
        when(brokerResearch.reanalyze(12L, 9L)).thenReturn(view);
        when(brokerResearch.upload(eq(7L), eq(9L), eq("贵州茅台深度报告"),
                eq("测试证券"), eq("张三"), eq(LocalDate.of(2026, 4, 20)),
                eq("买入"), eq("DEEP_DIVE"), eq(new BigDecimal("1800")),
                eq("research.pdf"), any(), anyLong())).thenReturn(view);

        mockMvc.perform(get("/api/financials/instruments/7/research-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("贵州茅台深度报告"));
        mockMvc.perform(get("/api/financials/research-reports/12")
                        .param("financialReportId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysis.executiveSummary[0]").value("品牌壁垒稳固"));

        MockMultipartFile file = new MockMultipartFile("file", "research.pdf",
                "application/pdf", "%PDF-1.4 sample".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/financials/research-reports/upload")
                        .file(file)
                        .param("instrumentId", "7")
                        .param("financialReportId", "9")
                        .param("title", "贵州茅台深度报告")
                        .param("institution", "测试证券")
                        .param("analyst", "张三")
                        .param("publishedDate", "2026-04-20")
                        .param("rating", "买入")
                        .param("reportType", "DEEP_DIVE")
                        .param("targetPrice", "1800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.id").value(12));

        mockMvc.perform(post("/api/financials/research-reports/12/reanalyze")
                        .param("financialReportId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.analysisStatus").value("LLM"));
    }

    @Test
    void streamsBrokerResearchPdfAsAnInlineLearningSource() throws Exception {
        byte[] bytes = "%PDF-1.4 research".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path path = tempDir.resolve("research.pdf");
        Files.write(path, bytes);
        BrokerResearchReport report = new BrokerResearchReport();
        report.setId(12L);
        report.setFileHash("research-hash");
        when(brokerResearch.require(12L)).thenReturn(report);
        when(brokerResearch.contentPath(12L)).thenReturn(path);

        mockMvc.perform(get("/api/financials/research-reports/12/content"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition", "inline; filename=\"research-hash.pdf\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().bytes(bytes));
    }

    @Test
    void exposesUserInitiatedBrokerResearchCandidatesAndImportContracts() throws Exception {
        BrokerResearchCandidate candidate = new BrokerResearchCandidate();
        candidate.setSourceCode("EASTMONEY");
        candidate.setExternalId("AP1");
        candidate.setTitle("贵州茅台公司点评");
        candidate.setAvailability("AVAILABLE");
        BrokerResearchSyncResult sync = new BrokerResearchSyncResult();
        sync.setStatus("SUCCESS");
        sync.setCandidates(Collections.singletonList(candidate));
        when(brokerResearchSync.candidates(7L)).thenReturn(sync);
        BrokerResearchReport report = new BrokerResearchReport();
        report.setId(81L);
        BrokerResearchReportView view = new BrokerResearchReportView();
        view.setReport(report);
        when(brokerResearchSync.importCandidate(7L, 9L, "EASTMONEY", "AP1"))
                .thenReturn(view);

        mockMvc.perform(get("/api/financials/instruments/7/research-reports/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates[0].externalId").value("AP1"));
        mockMvc.perform(post("/api/financials/instruments/7/research-reports/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCode\":\"EASTMONEY\",\"externalId\":\"AP1\"," +
                                "\"financialReportId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.id").value(81));
    }
}
