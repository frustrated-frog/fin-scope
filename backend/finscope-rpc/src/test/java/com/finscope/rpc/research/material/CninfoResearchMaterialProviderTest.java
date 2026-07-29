package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CninfoResearchMaterialProviderTest {
    @Test
    void resolvesOfficialOrgIdAndReturnsTraceableAnnouncements() {
        AcquisitionRuntime runtime = request -> {
            if (request.getUri().getPath().endsWith("szse_stock.json")) {
                return response(request, "{\"stockList\":[{\"code\":\"000001\",\"orgId\":\"gssz0000001\"}]}");
            }
            assertEquals("POST", request.getMethod());
            String body = new String(request.getBodyBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("stock=000001%2Cgssz0000001"));
            return response(request, "{\"announcements\":[{\"announcementId\":\"12001\","
                    + "\"announcementTitle\":\"2026年半年度报告\",\"announcementTypeName\":\"定期报告\","
                    + "\"announcementTime\":1785369600000}]}");
        };
        CninfoResearchMaterialProvider provider = new CninfoResearchMaterialProvider(runtime, new ObjectMapper());

        List<ResearchMaterial> result = provider.fetch(ResearchMaterialType.ANNOUNCEMENT,
                new ResearchMaterialRequest("000001", "半年度报告", 20)).getData();

        assertEquals(1, result.size());
        assertEquals("2026年半年度报告", result.get(0).getTitle());
        assertEquals("CNINFO", result.get(0).getProviderCode());
        assertEquals("T1", result.get(0).getSourceTier());
        assertTrue(result.get(0).getUrl().contains("annoId=12001"));
    }

    @Test
    void keepsOnlyAnsweredCompanyInteractionsAsEvidence() {
        AcquisitionRuntime runtime = request -> {
            if (request.getUri().getPath().endsWith("queryKeyboardInfo")) {
                return response(request, "{\"data\":[{\"secid\":\"gssz0000001\"}]}");
            }
            return response(request, "{\"rows\":["
                    + "{\"questionId\":\"q1\",\"stockCode\":\"000001\",\"companyShortName\":\"平安银行\","
                    + "\"mainContent\":\"资产质量如何？\",\"attachedContent\":\"不良率保持稳定。\","
                    + "\"attachedAuthor\":\"公司回复\",\"pubDate\":1785369600000},"
                    + "{\"questionId\":\"q2\",\"stockCode\":\"000001\",\"mainContent\":\"未回复问题\","
                    + "\"attachedContent\":null}]}");
        };
        CninfoResearchMaterialProvider provider = new CninfoResearchMaterialProvider(runtime, new ObjectMapper());

        List<ResearchMaterial> result = provider.fetch(ResearchMaterialType.INTERACTION,
                new ResearchMaterialRequest("000001", "资产质量", 20)).getData();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getContent().contains("不良率保持稳定"));
        assertEquals("T1", result.get(0).getSourceTier());
    }

    private static AcquisitionResponse response(AcquisitionRequest request, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        URI uri = request.getUri();
        return new AcquisitionResponse(uri, uri, 200, Collections.emptyMap(), bytes, body,
                "application/json", "UTF-8", "hash", 1, 1, Instant.now());
    }
}
