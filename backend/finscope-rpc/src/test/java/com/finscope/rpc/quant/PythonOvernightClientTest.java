package com.finscope.rpc.quant;

import com.finscope.common.enums.overnight.OvernightMode;
import com.finscope.common.enums.overnight.OvernightStatus;
import com.finscope.domain.quant.overnight.OvernightResearchInput;
import com.finscope.rpc.marketintel.FinanceHttpClient;
import com.finscope.rpc.marketintel.FinanceHttpResponse;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PythonOvernightClientTest {
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
    void acceptsCalibratedVersionAndPreservesItsEvidenceButRejectsUnknownVersions() {
        var input = new OvernightResearchInput();
        input.setMode(OvernightMode.TAIL_ENTRY);
        input.setInstrumentCode("605058.SH");
        input.setSignalDate(LocalDate.of(2026, 9, 16));
        input.setCutoff("14:30");
        var report = client("TAIL_ENTRY", "overnight-local-v3-calibrated").generate(input);
        assertEquals("overnight-local-v3-calibrated", report.getModelVersion());
        assertEquals(20, report.getTargets().get(0).get("calibrationCount"));
        assertEquals(Map.of("status", "BASELINE_NOT_BEATEN"), report.getTargets().get(0).get("reliability"));
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
                        + "\"reliability\":{\"status\":\"BASELINE_NOT_BEATEN\"}}],\"warnings\":[]}";
                return new FinanceHttpResponse(200, response, Instant.now(), "test");
            }
        };
        ReflectionTestUtils.setField(client, "http", http);
        ReflectionTestUtils.setField(client, "baseUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(client, "timeoutMs", 30000);
        return client;
    }
}
