package com.finscope.service.financials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.common.exception.BusinessException;
import com.finscope.domain.financials.BrokerResearchCandidate;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EastmoneyBrokerResearchSourceTest {

    @Test
    void mapsPublicCompanyReportCatalogAndLimitsRequestedSize() throws Exception {
        RecordingHttpClient http = new RecordingHttpClient();
        http.enqueue(json(200, "{\"data\":[{" +
                "\"title\":\"公司深度报告\",\"stockCode\":\"600519\"," +
                "\"orgSName\":\"测试证券\",\"publishDate\":\"2026-05-25 00:00:00.000\"," +
                "\"infoCode\":\"AP202605251822844635\",\"researcher\":\"张三\"," +
                "\"emRatingName\":\"买入\",\"reportType\":2,\"attachPages\":18" +
                "}],\"TotalPage\":1}"));
        EastmoneyBrokerResearchSource source = new EastmoneyBrokerResearchSource(
                http, new ObjectMapper().findAndRegisterModules());

        List<BrokerResearchCandidate> values = source.list("SH.600519",
                LocalDate.of(2025, 7, 19), LocalDate.of(2026, 7, 19), 20);

        assertEquals(1, values.size());
        BrokerResearchCandidate value = values.get(0);
        assertEquals("EASTMONEY", value.getSourceCode());
        assertEquals("AP202605251822844635", value.getExternalId());
        assertEquals("600519", value.getStockCode());
        assertEquals("测试证券", value.getInstitution());
        assertEquals("张三", value.getAnalyst());
        assertEquals(LocalDate.of(2026, 5, 25), value.getPublishedDate());
        assertEquals("买入", value.getRating());
        assertEquals(Integer.valueOf(18), value.getPageCount());
        assertTrue(value.getSourceUrl().endsWith("H3_AP202605251822844635_1.pdf"));
        assertTrue(http.lastUri.toString().contains("code=600519"));
        assertTrue(http.lastUri.toString().contains("pageSize=20"));
        assertTrue(http.lastUri.toString().contains("beginTime=2025-07-19"));
    }

    @Test
    void downloadsOnlyPublicPdfFromAllowedHost() throws Exception {
        RecordingHttpClient http = new RecordingHttpClient();
        http.enqueue(new BrokerResearchHttpClient.Response(200, "application/pdf",
                URI.create("https://pdf.dfcfw.com/pdf/H3_AP1_1.pdf"),
                "%PDF-valid".getBytes(StandardCharsets.US_ASCII)));
        EastmoneyBrokerResearchSource source = new EastmoneyBrokerResearchSource(
                http, new ObjectMapper());
        BrokerResearchCandidate candidate = candidate(
                "https://pdf.dfcfw.com/pdf/H3_AP1_1.pdf");

        byte[] content = source.download(candidate);

        assertEquals("%PDF-valid", new String(content, StandardCharsets.US_ASCII));
        assertEquals("https://data.eastmoney.com/", http.lastHeaders.get("Referer"));
    }

    @Test
    void rejectsForeignDownloadHostBeforeMakingRequest() {
        RecordingHttpClient http = new RecordingHttpClient();
        EastmoneyBrokerResearchSource source = new EastmoneyBrokerResearchSource(
                http, new ObjectMapper());

        assertThrows(BusinessException.class, () ->
                source.download(candidate("https://example.com/report.pdf")));
        assertEquals(0, http.calls);
    }

    @Test
    void rejectsNonPdfResponse() {
        RecordingHttpClient http = new RecordingHttpClient();
        http.enqueue(new BrokerResearchHttpClient.Response(200, "text/html",
                URI.create("https://pdf.dfcfw.com/error"),
                "<html>blocked</html>".getBytes(StandardCharsets.UTF_8)));
        EastmoneyBrokerResearchSource source = new EastmoneyBrokerResearchSource(
                http, new ObjectMapper());

        assertThrows(BusinessException.class, () ->
                source.download(candidate("https://pdf.dfcfw.com/pdf/H3_AP1_1.pdf")));
    }

    @Test
    void rejectsCatalogFailureWithoutInventingCandidates() {
        RecordingHttpClient http = new RecordingHttpClient();
        http.enqueue(json(503, "{\"message\":\"busy\"}"));
        EastmoneyBrokerResearchSource source = new EastmoneyBrokerResearchSource(
                http, new ObjectMapper());

        assertThrows(BusinessException.class, () -> source.list("600519",
                LocalDate.of(2025, 7, 19), LocalDate.of(2026, 7, 19), 20));
    }

    private BrokerResearchCandidate candidate(String url) {
        BrokerResearchCandidate value = new BrokerResearchCandidate();
        value.setSourceCode("EASTMONEY");
        value.setExternalId("AP1");
        value.setStockCode("600519");
        value.setSourceUrl(url);
        return value;
    }

    private BrokerResearchHttpClient.Response json(int status, String value) {
        return new BrokerResearchHttpClient.Response(status, "application/json",
                URI.create("https://reportapi.eastmoney.com/report/list"),
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingHttpClient implements BrokerResearchHttpClient {
        private final Queue<Response> responses = new ArrayDeque<Response>();
        private URI lastUri;
        private Map<String, String> lastHeaders = Collections.emptyMap();
        private int calls;

        private void enqueue(Response response) {
            responses.add(response);
        }

        @Override
        public Response get(URI uri, Map<String, String> headers, int maxBytes) {
            calls++;
            lastUri = uri;
            lastHeaders = headers;
            return responses.remove();
        }
    }
}
