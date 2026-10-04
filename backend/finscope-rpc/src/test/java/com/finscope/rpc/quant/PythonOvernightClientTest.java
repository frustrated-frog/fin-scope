package com.finscope.rpc.quant;

import com.finscope.common.enums.overnight.OvernightMode;
import com.finscope.common.enums.overnight.OvernightStatus;
import com.finscope.domain.quant.overnight.OvernightResearchInput;
import com.finscope.domain.quant.overnight.OvernightAutomationContext;
import com.finscope.domain.quant.overnight.OvernightLedgerPosition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PythonOvernightClientTest {
    @Test
    void synchronizesLedgerDatesAndCostsWithoutSendingValuationFields() throws Exception {
        var context = new OvernightAutomationContext();
        context.setEnabled(true);
        context.setCandidateLimit(6);
        var position = new OvernightLedgerPosition();
        position.setInstrumentCode("605058.SH");
        position.setQuantity(new BigDecimal("100"));
        position.setAverageCost(new BigDecimal("20.50"));
        position.setOpenedOn(LocalDate.of(2026, 9, 21));
        context.setPositions(List.of(position));
        var http = mock(FinanceHttpClient.class);
        when(http.postJson(anyString(), any(), anyString(), anyMap(), eq(3000)))
                .thenAnswer(invocation -> {
                    assertTrue(invocation.getArgument(1).toString().endsWith("/v1/quant/overnight/automation/context"));
                    var body = new ObjectMapper().readTree((String) invocation.getArgument(2));
                    assertEquals("2026-09-21", body.path("positions").get(0).path("openedOn").asText());
                    assertEquals(20.5, body.path("positions").get(0).path("averageCost").asDouble());
                    assertFalse(body.path("positions").get(0).has("lastPrice"));
                    return new FinanceHttpResponse(200, "{\"receivedAt\":\"2026-09-21T15:10:00\"}", Instant.now(), "test");
                });
        var client = new PythonOvernightClient();
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8000");
        client.syncAutomationContext(context);
        when(http.postJson(anyString(), any(), anyString(), anyMap(), eq(3000)))
                .thenReturn(new FinanceHttpResponse(503, "{}", Instant.now(), "test"));
        assertThrows(ProviderContractException.class, () -> client.syncAutomationContext(context));
    }

    @Test
    void mapsBlockedStateAndRejectsMismatchedMode() {
        var input = new OvernightResearchInput();
        input.setMode(OvernightMode.TAIL_ENTRY);
        input.setInstrumentCode("605058.SH");
        input.setSignalDate(LocalDate.of(2026, 9, 16));
        input.setCutoff("14:30");
        var client = client("TAIL_ENTRY");
        assertEquals(OvernightStatus.DATA_UNAVAILABLE, client.generate(input).getStatus());
        assertThrows(ProviderContractException.class, () -> client("AFTER_CLOSE_HOLDING").generate(input));
    }

    private PythonOvernightClient client(String mode) {
        return client(mode, "overnight-local-v1");
    }

    @Test
    void preservesSharedTrainingAndForwardValidationState() throws Exception {
        var http = mock(FinanceHttpClient.class);
        when(http.get(anyString(), any(), anyMap())).thenReturn(new FinanceHttpResponse(200,
                "{\"enabled\":true,\"jobs\":[],\"joint\":{\"protocol\":\"overnight-joint-v1\","
                    + "\"poolSize\":120,\"readySymbols\":23,\"forward\":{\"requiredDays\":60,\"groups\":[]}}}",
                Instant.now(), "test"));
        var client = new PythonOvernightClient();
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8000");
        var state = client.automationState();
        assertEquals("overnight-joint-v1", state.getJoint().get("protocol"));
        assertEquals(120, state.getJoint().get("poolSize"));
        assertEquals(Map.of("requiredDays", 60, "groups", List.of()), state.getJoint().get("forward"));
    }

    @Test
    void acceptsCalibratedVersionAndPreservesItsEvidenceButRejectsUnknownVersions() {
        var input = new OvernightResearchInput();
        input.setMode(OvernightMode.TAIL_ENTRY);
        input.setInstrumentCode("605058.SH");
        input.setSignalDate(LocalDate.of(2026, 9, 16));
        input.setCutoff("14:30");
        var report = client("TAIL_ENTRY", "overnight-local-v3-calibrated").generate(input);
        assertEquals("overnight-local-v3-calibrated", report.getModelVersion());
        assertEquals("overnight-local-v4-evidence-gated",
                client("TAIL_ENTRY", "overnight-local-v4-evidence-gated").generate(input).getModelVersion());
        assertEquals("overnight-v5-shared-evidence",
                client("TAIL_ENTRY", "overnight-v5-shared-evidence").generate(input).getModelVersion());
        assertEquals(20, report.getTargets().get(0).get("calibrationCount"));
        assertEquals(Map.of("status", "BASELINE_NOT_BEATEN"), report.getTargets().get(0).get("reliability"));
        assertEquals("SHADOW", report.getJointResearch().get("status"));
        assertThrows(ProviderContractException.class, () -> client("TAIL_ENTRY", "unknown").generate(input));
    }

    private PythonOvernightClient client(String mode, String version) {
        var client = new PythonOvernightClient();
        FinanceHttpClient http = new FinanceHttpClient() {
            @Override
            public FinanceHttpResponse get(String code, URI uri, Map<String, String> headers) {
                throw new UnsupportedOperationException();
            }
            @Override
            public FinanceHttpResponse postJson(String code, URI uri, String body,
                                                Map<String, String> headers, int timeout) {
                assertTrue(body.contains("\"signalDate\":\"2026-09-16\""));
                assertTrue(uri.toString().endsWith("/v1/quant/overnight/generate"));
                String response = "{\"mode\":\"" + mode + "\",\"status\":\"DATA_UNAVAILABLE\","
                        + "\"instrumentCode\":\"605058.SH\",\"signalDate\":\"2026-09-16\",\"cutoff\":\"14:30\","
                        + "\"modelVersion\":\"" + version + "\",\"dataThrough\":\"2026-09-16T14:30:00\","
                        + "\"inputFingerprint\":\"" + "a".repeat(64) + "\",\"evidenceKind\":\"RETROSPECTIVE\","
                        + "\"targets\":[{\"target\":\"OPEN\",\"calibrationCount\":20,"
                        + "\"reliability\":{\"status\":\"BASELINE_NOT_BEATEN\"}}],\"warnings\":[],"
                        + "\"jointResearch\":{\"status\":\"SHADOW\",\"protocol\":\"overnight-joint-v1\"}}";
                return new FinanceHttpResponse(200, response, Instant.now(), "test");
            }
        };
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(client, "timeoutMs", 30000);
        return client;
    }
}
