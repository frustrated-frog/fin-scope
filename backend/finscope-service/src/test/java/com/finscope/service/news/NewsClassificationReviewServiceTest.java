package com.finscope.service.news;

import com.finscope.common.exception.BusinessException;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.domain.news.NewsCategory;
import com.finscope.domain.news.NewsItemClassification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsClassificationReviewServiceTest {
    @Test
    void correctsClassificationAndReturnsBothAgentAndEffectiveDecisions() {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        NewsItemClassification before = classification(null, null, "PENDING_REVIEW");
        NewsItemClassification after = classification("INDUSTRY", "产业链影响", "CORRECTED");
        when(repository.findByItemIds(Collections.singleton("CLS:1")))
                .thenReturn(Collections.singletonMap("CLS:1", before))
                .thenReturn(Collections.singletonMap("CLS:1", after));
        when(categories.findEnabledByCode("INDUSTRY")).thenReturn(Optional.of(
                new NewsCategory("INDUSTRY", "行业产业", "产业链变化", true, 20)));
        when(repository.review("CLS:1", "INDUSTRY", "产业链影响",
                LocalDateTime.of(2026, 8, 1, 10, 0))).thenReturn(true);
        NewsClassificationReviewService service = new NewsClassificationReviewService(
                repository, categories, fixedClock());

        NewsClassificationView result = service.review(
                new NewsClassificationReviewRequest(" CLS:1 ", "industry", "产业链影响"));

        assertEquals("COMPANY", result.getAgentCategoryCode());
        assertEquals("INDUSTRY", result.getEffectiveCategoryCode());
        assertEquals("CORRECTED", result.getReviewStatus());
        verify(repository).review("CLS:1", "INDUSTRY", "产业链影响",
                LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    @Test
    void rejectsItemsWithoutCompletedAgentClassification() {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        when(repository.findByItemIds(Collections.singleton("CLS:1"))).thenReturn(Collections.singletonMap(
                "CLS:1", new NewsItemClassification("CLS:1", "PENDING", null, 0,
                        null, null, null, LocalDateTime.of(2026, 8, 1, 9, 0))));
        NewsClassificationReviewService service = new NewsClassificationReviewService(
                repository, mock(NewsCategoryRepository.class), fixedClock());

        BusinessException error = assertThrows(BusinessException.class, () -> service.review(
                new NewsClassificationReviewRequest("CLS:1", "COMPANY", "")));

        assertEquals("资讯尚未完成 Agent 分类，不能人工复核", error.getMessage());
    }

    @Test
    void rejectsDisabledOrUnknownTargetCategory() {
        NewsClassificationRepository repository = mock(NewsClassificationRepository.class);
        NewsCategoryRepository categories = mock(NewsCategoryRepository.class);
        when(repository.findByItemIds(Collections.singleton("CLS:1")))
                .thenReturn(Collections.singletonMap("CLS:1", classification(null, null, "PENDING_REVIEW")));
        when(categories.findEnabledByCode("UNKNOWN")).thenReturn(Optional.empty());
        NewsClassificationReviewService service = new NewsClassificationReviewService(
                repository, categories, fixedClock());

        BusinessException error = assertThrows(BusinessException.class, () -> service.review(
                new NewsClassificationReviewRequest("CLS:1", "UNKNOWN", "")));

        assertEquals("未知或已停用的资讯分类：UNKNOWN", error.getMessage());
    }

    private static NewsItemClassification classification(String manualCategory, String manualReason,
                                                         String reviewStatus) {
        return new NewsItemClassification("CLS:1", "CLASSIFIED", "COMPANY", 0.65,
                "公司公告", "model-a", null, manualCategory, manualReason, reviewStatus,
                manualCategory == null ? null : LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-01T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    }
}
