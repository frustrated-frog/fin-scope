package com.finscope.service.news;

import com.finscope.domain.research.material.ResearchMaterial;
import com.finscope.domain.research.material.ResearchMaterialType;
import com.finscope.service.research.material.ResearchMaterialGateway;
import com.finscope.service.research.material.ResearchMaterialGatewayResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsFeedServiceTest {
    @Test
    void reusesResearchMaterialGatewayAndSeparatesFlashFromDigest() {
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        when(gateway.search(eq(ResearchMaterialType.NEWS_FLASH), argThat(request ->
                request.getStockCode().equals("000001") && request.getQuery().isEmpty() && request.getLimit() == 50)))
                .thenReturn(new ResearchMaterialGatewayResult(Arrays.asList(
                        material("THS_NEWS_DIGEST", "THS", "专题要闻", "完整专题内容", 9, 30),
                        material("CLS_NEWS_FLASH", "CLS", "盘中快讯", "完整快讯内容", 9, 45)
                ), Collections.singletonList("备源暂不可用")));

        NewsFeedSnapshot result = new NewsFeedService(gateway, fixedClock()).load(80);

        assertEquals(2, result.getItems().size());
        assertEquals("FLASH", result.getItems().get(0).getKind());
        assertEquals("财联社", result.getItems().get(0).getSourceName());
        assertEquals("ARTICLE", result.getItems().get(1).getKind());
        assertEquals("同花顺", result.getItems().get(1).getSourceName());
        assertEquals("完整专题内容", result.getItems().get(1).getContent());
        assertEquals(2, result.getSourceCount());
        assertEquals(Collections.singletonList("备源暂不可用"), result.getWarnings());
        assertEquals(LocalDateTime.of(2026, 7, 30, 10, 0), result.getRefreshedAt());
    }

    @Test
    void deduplicatesCrossProviderItemsAndKeepsNewestFirst() {
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        ResearchMaterial older = material("THS_NEWS_FLASH", "THS", "相同消息", "较早内容", 9, 20);
        older.setUrl("https://example.com/same");
        ResearchMaterial newer = material("CLS_NEWS_FLASH", "CLS", "相同消息", "较新内容", 9, 50);
        newer.setUrl("https://example.com/same");
        when(gateway.search(eq(ResearchMaterialType.NEWS_FLASH), argThat(request -> true)))
                .thenReturn(new ResearchMaterialGatewayResult(Arrays.asList(older, newer), Collections.emptyList()));

        NewsFeedSnapshot result = new NewsFeedService(gateway, fixedClock()).load(20);

        assertEquals(1, result.getItems().size());
        assertEquals("较新内容", result.getItems().get(0).getContent());
        assertTrue(result.getWarnings().isEmpty());
    }

    private static ResearchMaterial material(String provider, String family, String title,
                                             String content, int hour, int minute) {
        ResearchMaterial value = new ResearchMaterial();
        value.setMaterialType(ResearchMaterialType.NEWS_FLASH);
        value.setExternalId(provider + "-" + minute);
        value.setTitle(title);
        value.setContent(content);
        value.setUrl("https://example.com/" + provider + "/" + minute);
        value.setPublishedAt(LocalDateTime.of(2026, 7, 30, hour, minute));
        value.setProviderCode(provider);
        value.setProviderFamily(family);
        value.setSourceTier("T2");
        return value;
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }
}
