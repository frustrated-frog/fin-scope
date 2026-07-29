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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClsNewsResearchMaterialProviderTest {
    @Test
    void signsRequestAndFiltersFlashNewsByResearchQuery() {
        AtomicReference<URI> requested = new AtomicReference<URI>();
        AcquisitionRuntime runtime = request -> {
            requested.set(request.getUri());
            String body = "{\"data\":{\"roll_data\":["
                    + "{\"id\":101,\"title\":\"机器人产业链订单增长\",\"content\":\"核心零部件需求提升\",\"ctime\":1785369600},"
                    + "{\"id\":102,\"title\":\"无关宏观消息\",\"content\":\"其他内容\",\"ctime\":1785369600}]}}";
            return response(request, body);
        };
        ClsNewsResearchMaterialProvider provider = new ClsNewsResearchMaterialProvider(runtime, new ObjectMapper());

        List<ResearchMaterial> result = provider.fetch(ResearchMaterialType.NEWS_FLASH,
                new ResearchMaterialRequest("000001", "银行 机器人", 20)).getData();

        assertTrue(requested.get().getQuery().contains("sign="));
        assertEquals(1, result.size());
        assertEquals("机器人产业链订单增长", result.get(0).getTitle());
        assertEquals("https://www.cls.cn/detail/101", result.get(0).getUrl());
        assertEquals("T2", result.get(0).getSourceTier());
    }

    private static AcquisitionResponse response(AcquisitionRequest request, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new AcquisitionResponse(request.getUri(), request.getUri(), 200, Collections.emptyMap(),
                bytes, body, "application/json", "UTF-8", "hash", 1, 1, Instant.now());
    }
}
