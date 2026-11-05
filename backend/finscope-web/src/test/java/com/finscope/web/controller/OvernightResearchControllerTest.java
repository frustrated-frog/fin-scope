package com.finscope.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.quant.PythonOvernightClient;
import com.finscope.service.quant.overnight.OvernightResearchService;
import com.finscope.service.strategy.holding.StockAccountService;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OvernightResearchController.class)
@Import({ApiExceptionHandler.class, FinScopeProperties.class, CorsConfig.class,
        OvernightResearchService.class, PythonOvernightClient.class})
class OvernightResearchControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private FinanceHttpClient http;
    @MockBean private StockAccountService accounts;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void readsAndSettlesMixedPythonArchivesThroughTheRealClientAndService() throws Exception {
        ArrayNode reports = fixture();
        ObjectNode legacy = ((ObjectNode) reports.get(1)).deepCopy();
        legacy.put("modelVersion", "overnight-v5-shared-evidence");
        legacy.remove("acquisition");
        reports.add(legacy);
        FinanceHttpResponse response = response(reports.toString());
        when(http.get(anyString(), any(), anyMap(), anyInt(), anyInt())).thenReturn(response);
        when(http.postJson(anyString(), any(), anyString(), anyMap(), anyInt(), anyInt())).thenReturn(response);

        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                get("/api/quant/overnight/history"), post("/api/quant/overnight/settle")}) {
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].mode").value("AFTER_CLOSE_HOLDING"))
                    .andExpect(jsonPath("$.data[1].mode").value("TAIL_ENTRY"))
                    .andExpect(jsonPath("$.data[1].modelVersion").value("overnight-v6-decision-direction"))
                    .andExpect(jsonPath("$.data[1].acquisition.source").value("SYNTHETIC_FALLBACK"))
                    .andExpect(jsonPath("$.data[1].acquisition.fallbackReasons[0].code").value("TIMEOUT"))
                    .andExpect(jsonPath("$.data[2].modelVersion").value("overnight-v5-shared-evidence"));
        }
    }

    @Test
    void generatesThePythonReportWithoutLosingItsDecisionPriceOrProvenance() throws Exception {
        var report = fixture().get(1);
        when(http.postJson(anyString(), any(), anyString(), anyMap(), anyInt()))
                .thenReturn(response(report.toString()));
        mvc.perform(post("/api/quant/overnight/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(report.get("request").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referencePrice").value(report.get("referencePrice").doubleValue()))
                .andExpect(jsonPath("$.data.inputFingerprint").value(report.get("inputFingerprint").asText()))
                .andExpect(jsonPath("$.data.acquisition.status").value("AVAILABLE"));
    }

    private FinanceHttpResponse response(String body) {
        return new FinanceHttpResponse(200, body, Instant.parse("2026-01-08T07:01:00Z"), "synthetic");
    }

    private ArrayNode fixture() throws Exception {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path fixture = directory.resolve("test-fixtures/overnight/history-v6.json");
            if (Files.exists(fixture)) {
                return (ArrayNode) json.readTree(Files.readString(fixture));
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Missing shared synthetic overnight contract fixture");
    }
}
