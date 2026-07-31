package com.finscope.service.news;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.domain.news.NewsItemClassification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

@Service
public class NewsClassificationReviewService {
    private final NewsClassificationRepository repository;
    private final NewsCategoryRepository categories;
    private final Clock clock;

    @Autowired
    public NewsClassificationReviewService(NewsClassificationRepository repository,
                                           NewsCategoryRepository categories) {
        this(repository, categories, Clock.systemDefaultZone());
    }

    NewsClassificationReviewService(NewsClassificationRepository repository,
                                    NewsCategoryRepository categories,
                                    Clock clock) {
        this.repository = repository;
        this.categories = categories;
        this.clock = clock;
    }

    public NewsClassificationView review(NewsClassificationReviewRequest request) {
        if (request == null) {
            throw invalid("分类复核请求不能为空");
        }
        String itemId = trim(request.getItemId());
        if (itemId == null) {
            throw invalid("资讯 ID 不能为空");
        }
        String categoryCode = trim(request.getCategoryCode());
        if (categoryCode == null) {
            throw invalid("目标分类不能为空");
        }
        categoryCode = categoryCode.toUpperCase(Locale.ROOT);

        NewsItemClassification current = find(itemId);
        if (current == null || !"CLASSIFIED".equals(current.getStatus())) {
            throw invalid("资讯尚未完成 Agent 分类，不能人工复核");
        }
        if (!categories.findEnabledByCode(categoryCode).isPresent()) {
            throw invalid("未知或已停用的资讯分类：" + categoryCode);
        }
        String reason = trim(request.getReason());
        if (!repository.review(itemId, categoryCode, reason, LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT, "分类结果已变化，请刷新后重试");
        }
        NewsItemClassification updated = find(itemId);
        if (updated == null) {
            throw new BusinessException(ErrorCode.DATA_INTEGRITY_ERROR, "分类复核结果读取失败");
        }
        return view(updated);
    }

    private NewsItemClassification find(String itemId) {
        Map<String, NewsItemClassification> values = repository.findByItemIds(Collections.singleton(itemId));
        return values.get(itemId);
    }

    private NewsClassificationView view(NewsItemClassification value) {
        return new NewsClassificationView(value.getItemId(), value.getCategoryCode(),
                value.getEffectiveCategoryCode(), value.getConfidence(), value.getReason(),
                value.getReviewStatus(), value.getManualReason(), value.getReviewedAt());
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, message);
    }

    private String trim(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
