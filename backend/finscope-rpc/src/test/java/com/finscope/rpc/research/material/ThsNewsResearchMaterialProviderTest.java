package com.finscope.rpc.research.material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.rpc.acquisition.AcquisitionRequest;
import com.finscope.rpc.acquisition.AcquisitionResponse;
import com.finscope.rpc.acquisition.AcquisitionRuntime;
import com.finscope.rpc.acquisition.AcquisitionErrorType;
import com.finscope.rpc.acquisition.AcquisitionException;
import com.finscope.rpc.marketdata.ProviderResult;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.marketintel.ProviderRequestGuard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThsNewsResearchMaterialProviderTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void parsesRealtimeJavascriptAndKeepsCompleteMatchingNews() {
        AtomicReference<AcquisitionRequest> requested = new AtomicReference<AcquisitionRequest>();
        AcquisitionRuntime runtime = request -> {
            requested.set(request);
            return response(request, "var thsRss = {pubDate:\"2026/07/30 09:58:12\",item:["
                    + "{seq:678524843,isvalid:\"1\",title:\"机器人订单增长\","
                    + "content:\"机器人核心零部件订单增长，海外客户需求同步提升。\","
                    + "url:\"http://stock.10jqka.com.cn/20260730/c678524843.shtml\","
                    + "pubDate:\"2026/07/30 09:55\"},"
                    + "{seq:678524842,isvalid:\"0\",title:\"机器人无效稿\",content:\"不应返回\","
                    + "pubDate:\"2026/07/30 09:54\"},"
                    + "{seq:678524839,title:\"机器人缺少有效标记\",content:\"不完整条目不应返回\","
                    + "pubDate:\"2026/07/30 09:54\"},"
                    + "{seq:678524841,isvalid:\"1\",title:\"宏观消息\",content:\"与查询无关\","
                    + "pubDate:\"2026/07/30 09:53\"},"
                    + "{seq:678524840,isvalid:\"1\",title:\"机器人时间错误\",content:\"坏条目不破坏整批\","
                    + "pubDate:\"invalid\"}]};");
        };
        ThsRealtimeNewsResearchMaterialProvider provider =
                new ThsRealtimeNewsResearchMaterialProvider(runtime, new ObjectMapper(), CLOCK);

        ProviderResult<List<ResearchMaterial>> fetched = provider.fetch(
                ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "机器人", 20));

        assertEquals("https://stock.10jqka.com.cn/thsgd/realtimenews.js",
                requested.get().getUri().toString());
        assertEquals("THS_NEWS_FLASH", requested.get().getPurpose());
        assertEquals("https://news.10jqka.com.cn/", requested.get().getHeaders().get("Referer"));
        assertEquals(1, fetched.getData().size());
        ResearchMaterial material = fetched.getData().get(0);
        assertEquals("678524843", material.getExternalId());
        assertEquals("机器人订单增长", material.getTitle());
        assertEquals("机器人核心零部件订单增长，海外客户需求同步提升。", material.getContent());
        assertEquals("https://stock.10jqka.com.cn/20260730/c678524843.shtml", material.getUrl());
        assertEquals("THS_NEWS_FLASH", material.getProviderCode());
        assertEquals("THS", material.getProviderFamily());
        assertEquals("T2", material.getSourceTier());
        assertTrue(fetched.getWarnings().isEmpty());
    }

    @Test
    void exposesHeadlineDigestAsIndependentProviderAndHonorsLimit() {
        AtomicReference<AcquisitionRequest> requested = new AtomicReference<AcquisitionRequest>();
        AcquisitionRuntime runtime = request -> {
            requested.set(request);
            return response(request, "var thsRss = {pubDate:\"2026/07/30 09:58:12\",item:["
                    + item(11, "上市公司要闻一", "第一条完整内容", "09:55") + ","
                    + item(12, "上市公司要闻二", "第二条完整内容", "09:54") + "]};");
        };
        ThsHeadlineNewsResearchMaterialProvider provider =
                new ThsHeadlineNewsResearchMaterialProvider(runtime, new ObjectMapper(), CLOCK);

        ProviderResult<List<ResearchMaterial>> fetched = provider.fetch(
                ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "上市公司", 1));

        assertEquals("THS_NEWS_DIGEST", provider.providerCode());
        assertEquals("THS", provider.providerFamily());
        assertEquals(20, provider.priority());
        assertEquals("https://stock.10jqka.com.cn/thsgd/ywjh.js",
                requested.get().getUri().toString());
        assertEquals(1, fetched.getData().size());
        assertEquals("上市公司要闻一", fetched.getData().get(0).getTitle());
    }

    @Test
    void rejectsUnexpectedJavascriptContract() {
        AcquisitionRuntime runtime = request -> response(request, "window.other = {item:[]};");
        ThsRealtimeNewsResearchMaterialProvider provider =
                new ThsRealtimeNewsResearchMaterialProvider(runtime, new ObjectMapper(), CLOCK);

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(ResearchMaterialType.NEWS_FLASH,
                        new ResearchMaterialRequest("000001", "", 20)));

        assertEquals("INVALID_RESPONSE", error.getErrorType());
        assertFalse(error.isRetryable());
    }

    @Test
    void rejectsResponseWithoutItemArray() {
        AcquisitionRuntime runtime = request -> response(request,
                "var thsRss = {pubDate:\"2026/07/30 09:58:12\"};");
        ThsRealtimeNewsResearchMaterialProvider provider =
                new ThsRealtimeNewsResearchMaterialProvider(runtime, new ObjectMapper(), CLOCK);

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(ResearchMaterialType.NEWS_FLASH,
                        new ResearchMaterialRequest("000001", "", 20)));

        assertEquals("INVALID_RESPONSE", error.getErrorType());
        assertTrue(error.getMessage().contains("item"));
    }

    @Test
    void warnsWhenFeedHasNotBeenUpdatedRecently() {
        AcquisitionRuntime runtime = request -> response(request,
                "var thsRss = {pubDate:\"2026/07/27 09:58:12\",item:["
                        + item(21, "历史快讯", "仍可引用的完整内容", "09:55") + "]};");
        ThsRealtimeNewsResearchMaterialProvider provider =
                new ThsRealtimeNewsResearchMaterialProvider(runtime, new ObjectMapper(), CLOCK);

        ProviderResult<List<ResearchMaterial>> fetched = provider.fetch(
                ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "", 20));

        assertEquals(1, fetched.getData().size());
        assertEquals(1, fetched.getWarnings().size());
        assertTrue(fetched.getWarnings().get(0).contains("超过 24 小时未更新"));
    }

    @Test
    void preservesRetryableAcquisitionFailureForGuardPolicy() {
        AcquisitionException timeout = new AcquisitionException(
                AcquisitionErrorType.TIMEOUT, "上游读取超时", true, null);
        AcquisitionRuntime runtime = request -> { throw timeout; };
        ThsRealtimeNewsResearchMaterialProvider provider =
                new ThsRealtimeNewsResearchMaterialProvider(runtime, new ObjectMapper(), CLOCK);

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> provider.fetch(ResearchMaterialType.NEWS_FLASH,
                        new ResearchMaterialRequest("000001", "", 20)));

        assertEquals("TIMEOUT", error.getErrorType());
        assertTrue(error.isRetryable());
        assertEquals(timeout, error.getCause());
    }

    @Test
    void keepsDigestAvailableWhenRealtimeEndpointCircuitOpens() {
        AcquisitionRuntime failedRuntime = request -> {
            throw new AcquisitionException(AcquisitionErrorType.CONNECTION_ERROR,
                    "实时端点连接失败", true, null);
        };
        ThsRealtimeNewsResearchMaterialProvider realtime =
                new ThsRealtimeNewsResearchMaterialProvider(failedRuntime, new ObjectMapper(), CLOCK);
        ThsHeadlineNewsResearchMaterialProvider digest =
                new ThsHeadlineNewsResearchMaterialProvider(request -> response(request,
                        "var thsRss = {pubDate:\"2026/07/30 09:58:12\",item:[]};"),
                        new ObjectMapper(), CLOCK);
        ProviderRequestGuard guard = new ProviderRequestGuard(
                CLOCK, millis -> { }, java.time.Duration.ZERO, 0, 1, java.time.Duration.ofMinutes(1));
        String capability = "RESEARCH_MATERIAL_NEWS_FLASH";

        ProviderContractException error = assertThrows(ProviderContractException.class,
                () -> guard.execute(realtime, capability,
                        () -> realtime.fetch(ResearchMaterialType.NEWS_FLASH,
                                new ResearchMaterialRequest("000001", "", 20))));

        assertTrue(error.isRetryable());
        assertFalse(guard.isAvailable(realtime, capability));
        assertTrue(guard.isAvailable(digest, capability));
    }

    private static String item(long seq, String title, String content, String time) {
        return "{seq:" + seq + ",isvalid:\"1\",title:\"" + title + "\",content:\""
                + content + "\",url:\"http://news.10jqka.com.cn/item/" + seq
                + "\",pubDate:\"2026/07/30 " + time + "\"}";
    }

    private static AcquisitionResponse response(AcquisitionRequest request, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new AcquisitionResponse(request.getUri(), request.getUri(), 200, Collections.emptyMap(),
                bytes, body, "application/javascript", "UTF-8", "body-hash", 1, 1, Instant.now());
    }
}
