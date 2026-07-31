package com.finscope.service.news;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.NewsItemClassification;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void returnsAllItemsImmediatelyAndSchedulesAgentClassification() {
        ResearchMaterialGateway gateway = gatewayWithTwoItems();
        NewsClassificationRepository classifications = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsClassificationCoordinator coordinator = mock(NewsClassificationCoordinator.class);
        when(classifications.findByItemIds(argThat(ids -> ids.size() == 2))).thenReturn(Collections.emptyMap());
        NewsFeedService service = new NewsFeedService(gateway, classifications, categories, coordinator, fixedClock());

        NewsFeedSnapshot result = service.load("ALL", 20);

        assertEquals(2, result.getItems().size());
        verify(coordinator).schedule(argThat(values -> values.size() == 2));
    }

    @Test
    void filtersSpecificCategoryUsingPersistedAgentDecision() {
        ResearchMaterialGateway gateway = gatewayWithTwoItems();
        NewsClassificationRepository classifications = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsClassificationCoordinator coordinator = mock(NewsClassificationCoordinator.class);
        when(categories.findEnabledByCode("COMPANY")).thenReturn(java.util.Optional.of(category("COMPANY", "公司动态")));
        Map<String, NewsItemClassification> saved = new LinkedHashMap<String, NewsItemClassification>();
        saved.put("CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", classification("CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", "COMPANY"));
        saved.put("THS_NEWS_DIGEST:THS_NEWS_DIGEST-30", classification("THS_NEWS_DIGEST:THS_NEWS_DIGEST-30", "INDUSTRY"));
        when(classifications.findByItemIds(argThat(ids -> ids.size() == 2))).thenReturn(saved);
        NewsFeedService service = new NewsFeedService(gateway, classifications, categories, coordinator, fixedClock());

        NewsFeedSnapshot result = service.load("COMPANY", 20);

        assertEquals(1, result.getItems().size());
        assertEquals("盘中快讯", result.getItems().get(0).getTitle());
        assertEquals("COMPANY", result.getItems().get(0).getCategoryCode());
        assertEquals("公司动态", result.getItems().get(0).getCategoryName());
    }

    @Test
    void rejectsUnknownCategoryBeforeFetchingNews() {
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsFeedService service = new NewsFeedService(gateway, mock(NewsClassificationRepository.class),
                categories, mock(NewsClassificationCoordinator.class), fixedClock());

        try {
            service.load("UNKNOWN", 20);
        } catch (BusinessException ex) {
            assertEquals("未知或已停用的资讯分类：UNKNOWN", ex.getMessage());
            return;
        }
        throw new AssertionError("expected BusinessException");
    }

    @Test
    void returnsOnlyLowConfidenceUnreviewedItemsForPendingReview() {
        ResearchMaterialGateway gateway = gatewayWithTwoItems();
        NewsClassificationRepository classifications = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        Map<String, NewsItemClassification> saved = new LinkedHashMap<String, NewsItemClassification>();
        saved.put("CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", classification(
                "CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", "COMPANY", 0.65, null, "PENDING_REVIEW"));
        saved.put("THS_NEWS_DIGEST:THS_NEWS_DIGEST-30", classification(
                "THS_NEWS_DIGEST:THS_NEWS_DIGEST-30", "INDUSTRY", 0.92, null, "AUTO_CONFIRMED"));
        when(classifications.findByItemIds(argThat(ids -> ids.size() == 2))).thenReturn(saved);
        NewsFeedService service = new NewsFeedService(gateway, classifications, categories,
                mock(NewsClassificationCoordinator.class), fixedClock());

        NewsFeedSnapshot result = service.load("PENDING_REVIEW", 20);

        assertEquals(1, result.getItems().size());
        assertEquals("盘中快讯", result.getItems().get(0).getTitle());
        assertEquals("PENDING_REVIEW", result.getItems().get(0).getReviewStatus());
    }

    @Test
    void filtersBusinessCategoryUsingManualEffectiveDecision() {
        ResearchMaterialGateway gateway = gatewayWithTwoItems();
        NewsClassificationRepository classifications = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        when(categories.findEnabledByCode("INDUSTRY")).thenReturn(java.util.Optional.of(
                category("INDUSTRY", "行业产业")));
        Map<String, NewsItemClassification> saved = new LinkedHashMap<String, NewsItemClassification>();
        saved.put("CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", classification(
                "CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", "COMPANY", 0.65, "INDUSTRY", "CORRECTED"));
        when(classifications.findByItemIds(argThat(ids -> ids.size() == 2))).thenReturn(saved);
        NewsFeedService service = new NewsFeedService(gateway, classifications, categories,
                mock(NewsClassificationCoordinator.class), fixedClock());

        NewsFeedSnapshot result = service.load("INDUSTRY", 20);

        assertEquals(1, result.getItems().size());
        assertEquals("INDUSTRY", result.getItems().get(0).getCategoryCode());
        assertEquals("COMPANY", result.getItems().get(0).getAgentCategoryCode());
        assertTrue(result.getItems().get(0).isManuallyReviewed());
    }

    @Test
    void reportsCategoryCountsAndUnclassifiedCountForCurrentSnapshot() {
        ResearchMaterialGateway gateway = gatewayWithTwoItems();
        NewsClassificationRepository classifications = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        when(categories.findEnabled()).thenReturn(Arrays.asList(
                category("COMPANY", "公司动态"), category("INDUSTRY", "行业产业")));
        Map<String, NewsItemClassification> saved = new LinkedHashMap<String, NewsItemClassification>();
        saved.put("CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", classification(
                "CLS_NEWS_FLASH:CLS_NEWS_FLASH-45", "COMPANY", 0.65, null, "PENDING_REVIEW"));
        when(classifications.findByItemIds(argThat(ids -> ids.size() == 2))).thenReturn(saved);
        NewsFeedService service = new NewsFeedService(gateway, classifications, categories,
                mock(NewsClassificationCoordinator.class), fixedClock());

        NewsFeedSnapshot result = service.load("ALL", 20);

        assertEquals(Integer.valueOf(2), result.getCategoryCounts().get("ALL"));
        assertEquals(Integer.valueOf(1), result.getCategoryCounts().get("COMPANY"));
        assertEquals(Integer.valueOf(0), result.getCategoryCounts().get("INDUSTRY"));
        assertEquals(Integer.valueOf(1), result.getCategoryCounts().get("PENDING_REVIEW"));
        assertEquals(1, result.getUnclassifiedCount());
    }

    private static ResearchMaterialGateway gatewayWithTwoItems() {
        ResearchMaterialGateway gateway = mock(ResearchMaterialGateway.class);
        when(gateway.search(eq(ResearchMaterialType.NEWS_FLASH), argThat(request -> true)))
                .thenReturn(new ResearchMaterialGatewayResult(Arrays.asList(
                        material("THS_NEWS_DIGEST", "THS", "专题要闻", "完整专题内容", 9, 30),
                        material("CLS_NEWS_FLASH", "CLS", "盘中快讯", "完整快讯内容", 9, 45)
                ), Collections.emptyList()));
        return gateway;
    }

    private static NewsCategory category(String code, String name) {
        return new NewsCategory(code, name, name + "指导", true, 10);
    }

    private static NewsItemClassification classification(String itemId, String categoryCode) {
        return new NewsItemClassification(itemId, "CLASSIFIED", categoryCode, 0.9,
                "Agent 分类", "model-a", null, LocalDateTime.of(2026, 7, 31, 10, 0));
    }

    private static NewsItemClassification classification(String itemId, String categoryCode, double confidence,
                                                         String manualCategoryCode, String reviewStatus) {
        return new NewsItemClassification(itemId, "CLASSIFIED", categoryCode, confidence,
                "Agent 分类", "model-a", null, manualCategoryCode, null, reviewStatus,
                manualCategoryCode == null ? null : LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0));
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
