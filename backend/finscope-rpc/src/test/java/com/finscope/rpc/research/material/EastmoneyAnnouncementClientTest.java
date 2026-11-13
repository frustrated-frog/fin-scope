package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EastmoneyAnnouncementClientTest {
    @Test
    void usesActualPublicationTimeEvenWhenNoticeDateIsTomorrowAndKeepsPagination() {
        var requests = new java.util.ArrayList<AcquisitionRequest>();
        var client = client(request -> {
            requests.add(request);
            return response(request, """
                    {"success":1,"data":{"total_hits":101,"list":[
                    {"art_code":"AN202611131831691800","title":"杭电股份:关于回购股份事项前十名股东持股情况的公告",
                    "codes":[{"stock_code":"603618"}],"notice_date":"2026-11-14 00:00:00","display_time":"2026-11-13 16:21:58:346"},
                    {"art_code":"AN202611121831660465","title":"杭电股份:关于取得股票回购专项贷款承诺函的公告",
                    "codes":[{"stock_code":"603618"}],"notice_date":"2026-11-13 00:00:00","display_time":"2026-11-12 17:33:44:319"},
                    {"art_code":"AN0001","title":"无公开时间","codes":[{"stock_code":"603618"}],"notice_date":"2026-11-13"}
                    ]}}
                    """);
        });
        var result = client.page(LocalDateTime.parse("2026-11-11T20:00:00"), LocalDateTime.parse("2026-11-13T20:00:00"), 1);
        assertTrue(result.isHasMore());
        assertEquals(2, result.getMaterials().size());
        assertEquals(LocalDateTime.parse("2026-11-13T16:21:58"), result.getMaterials().get(0).getPublishedAt());
        assertTrue(result.getMaterials().get(0).getContent().contains("2026-11-14"));
        assertTrue(requests.get(0).getUri().getQuery().contains("end_time=2026-11-14"));
    }

    @Test
    void validatesContentIdentityAndDoesNotOverwritePublicationTime() {
        var client = client(request -> response(request, "{}"));
        com.finscope.rpc.research.ResearchSourceReader reader = url -> new com.finscope.domain.research.ResearchSourceDocument(
                url, "公司公告", "证券代码：603618。公司于2026年11月11日召开董事会，审议通过回购预案。",
                "application/pdf", "PDF_TEXT", "SUCCESS");
        ReflectionTestUtils.setField(client, "sourceReader", reader);
        var value = new com.finscope.domain.research.material.ResearchMaterial();
        value.setExternalId("AN123");
        value.setStockCode("603618");
        value.setContent("公告日期：2026-11-14");
        value.setPublishedAt(LocalDateTime.parse("2026-11-13T16:21:58"));
        client.enrich(value);
        assertTrue(value.getContent().contains("公告原文："));
        assertEquals(LocalDateTime.parse("2026-11-13T16:21:58"), value.getPublishedAt());
        value.setStockCode("600000");
        assertThrows(com.finscope.rpc.marketintel.ProviderContractException.class, () -> client.enrich(value));
    }

    private EastmoneyAnnouncementClient client(AcquisitionRuntime runtime) {
        var value = new EastmoneyAnnouncementClient();
        ReflectionTestUtils.setField(value, "runtime", runtime);
        ReflectionTestUtils.setField(value, "json", new ObjectMapper());
        return value;
    }

    private AcquisitionResponse response(AcquisitionRequest request, String body) {
        return new AcquisitionResponse(request.getUri(), request.getUri(), 200, Map.of(),
                body.getBytes(StandardCharsets.UTF_8), body, "application/json", "UTF-8", "hash", 1, 1, Instant.now());
    }
}
