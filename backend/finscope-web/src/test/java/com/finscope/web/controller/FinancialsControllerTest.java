package com.finscope.web.controller;

import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.financials.FinancialReportType;
import com.finscope.domain.financials.FinancialReportView;
import com.finscope.domain.financials.FinancialDocument;
import com.finscope.domain.instrument.Instrument;
import com.finscope.service.financials.FinancialDocumentService;
import com.finscope.service.financials.FinancialQueryService;
import com.finscope.service.financials.FinancialRefreshService;
import com.finscope.web.config.CorsConfig;
import com.finscope.web.config.FinScopeProperties;
import com.finscope.web.handler.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialsController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class})
class FinancialsControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private FinancialQueryService query;
    @MockBean
    private FinancialRefreshService refresh;
    @MockBean
    private FinancialDocumentService documents;

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
    void refreshesRequestedReportAndReturnsAcceptedEnvelope() throws Exception {
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
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.report.id").value(9))
                .andExpect(jsonPath("$.data.report.reportType").value("HALF_YEAR"));
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
}
