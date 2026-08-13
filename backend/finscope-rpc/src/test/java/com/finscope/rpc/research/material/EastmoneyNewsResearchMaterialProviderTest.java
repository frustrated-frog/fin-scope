package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.common.enums.research.ResearchMaterialType;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EastmoneyNewsResearchMaterialProviderTest {
    @Test
    void parsesFullMarketFlashNewsAndMapsProviderMetadata() {
        AtomicReference<AcquisitionRequest> requested = new AtomicReference<AcquisitionRequest>();
        AcquisitionRuntime runtime = request -> {
            requested.set(request);
            return response(request, "var ajaxResult={\"rc\":1,\"LivesList\":["
                    + "{\"id\":\"202608101001\",\"title\":\"机器人产业链订单增长\","
                    + "\"digest\":\"核心零部件需求提升\",\"showtime\":\"2026-08-10 10:01:02\","
                    + "\"url_unique\":\"http://finance.eastmoney.com/a/202608101001.html\"},"
                    + "{\"id\":\"202608101000\",\"title\":\"无关宏观消息\","
                    + "\"digest\":\"其他内容\",\"showtime\":\"2026-08-10 10:00:00\"}]};");
        };
        EastmoneyNewsResearchMaterialProvider provider =
                new EastmoneyNewsResearchMaterialProvider(runtime, new ObjectMapper());

        ProviderResult<List<ResearchMaterial>> fetched = provider.fetch(
                ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "银行 机器人", 20));

        assertEquals("https://newsapi.eastmoney.com/kuaixun/v1/getlist_102_ajaxResult_20_1_.html",
                requested.get().getUri().toString());
        assertEquals("EASTMONEY_NEWS_FLASH", requested.get().getPurpose());
        assertEquals("https://kuaixun.eastmoney.com/", requested.get().getHeaders().get("Referer"));
        assertEquals(1, fetched.getData().size());
        ResearchMaterial material = fetched.getData().get(0);
        assertEquals("202608101001", material.getExternalId());
        assertEquals("机器人产业链订单增长", material.getTitle());
        assertEquals("核心零部件需求提升", material.getContent());
        assertEquals("https://finance.eastmoney.com/a/202608101001.html", material.getUrl());
        assertEquals(LocalDateTime.of(2026, 8, 10, 10, 1, 2), material.getPublishedAt());
        assertEquals("EASTMONEY_NEWS_FLASH", material.getProviderCode());
        assertEquals("EASTMONEY", material.getProviderFamily());
        assertEquals("T2", material.getSourceTier());
        assertEquals("EASTMONEY_NEWS_FLASH", provider.reliabilityFamily());
        assertEquals(50, provider.batchLimit());
        assertTrue(provider.materialTypes().contains(ResearchMaterialType.NEWS_FLASH));
    }

    @Test
    void usesBoundaryFieldsAndKeepsBatchWhenPublishedTimeIsInvalid() {
        AcquisitionRuntime runtime = request -> response(request,
                "var ajaxResult={\"LivesList\":["
                        + "{\"newsid\":\"fallback-1\",\"simtitle\":\"算力中心投产\","
                        + "\"simdigest\":\"新增算力投入使用\",\"ordertime\":\"invalid\","
                        + "\"url_w\":\"http://kuaixun.eastmoney.com/news/fallback-1\"},"
                        + "{\"id\":\"missing-title\",\"digest\":\"没有标题\"},"
                        + "{\"title\":\"没有稳定编号\",\"digest\":\"应被跳过\"},"
                        + "{\"id\":\"title-content\",\"title\":\"摘要缺失时使用标题\"}]};");
        EastmoneyNewsResearchMaterialProvider provider =
                new EastmoneyNewsResearchMaterialProvider(runtime, new ObjectMapper());

        List<ResearchMaterial> result = provider.fetch(ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "", 50)).getData();

        assertEquals(2, result.size());
        assertEquals("fallback-1", result.get(0).getExternalId());
        assertEquals("算力中心投产", result.get(0).getTitle());
        assertEquals("新增算力投入使用", result.get(0).getContent());
        assertEquals("https://kuaixun.eastmoney.com/news/fallback-1", result.get(0).getUrl());
        assertNull(result.get(0).getPublishedAt());
        assertEquals("摘要缺失时使用标题", result.get(1).getContent());
    }

    @Test
    void rejectsResponseWithoutLivesList() {
        AcquisitionRuntime runtime = request -> response(request, "var ajaxResult={\"rc\":1};");
        EastmoneyNewsResearchMaterialProvider provider =
                new EastmoneyNewsResearchMaterialProvider(runtime, new ObjectMapper());

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(ResearchMaterialType.NEWS_FLASH,
                        new ResearchMaterialRequest("000001", "", 20)));

        assertEquals("INVALID_RESPONSE", error.getErrorType());
        assertFalse(error.isRetryable());
        assertTrue(error.getMessage().contains("LivesList"));
    }

    @Test
    void rejectsUnexpectedJavascriptWrapper() {
        AcquisitionRuntime runtime = request -> response(request, "window.other={\"LivesList\":[]};");
        EastmoneyNewsResearchMaterialProvider provider =
                new EastmoneyNewsResearchMaterialProvider(runtime, new ObjectMapper());

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(ResearchMaterialType.NEWS_FLASH,
                        new ResearchMaterialRequest("000001", "", 20)));

        assertEquals("INVALID_RESPONSE", error.getErrorType());
    }

    private static AcquisitionResponse response(AcquisitionRequest request, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new AcquisitionResponse(request.getUri(), request.getUri(), 200, Collections.emptyMap(),
                bytes, body, "application/javascript", "UTF-8", "body-hash", 1, 1, Instant.now());
    }
}
