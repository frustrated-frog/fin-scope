package com.finscope.service.news;

import com.finscope.service.cache.ViewRevisionService;

import com.finscope.dao.agent.AgentRunRepository;
import com.finscope.dao.news.NewsCategoryRepository;
import com.finscope.dao.news.NewsClassificationRepository;
import com.finscope.domain.news.NewsCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@lombok.extern.slf4j.Slf4j
@Service
public class NewsClassificationCoordinator {
    @Autowired
    private NewsWorkbenchCapabilities capabilities;
    @Autowired
    private NewsRuleClassifier rules;
    @Autowired
    private ViewRevisionService revisions;
    private static final int RETRY_MINUTES = 5;
    private static final int BATCH_SIZE = 12;
    @Autowired
    private NewsClassificationRepository repository;
    @Autowired
    private NewsCategoryRepository categories;
    @Autowired
    private NewsClassificationAgent agent;
    @Autowired
    private AgentRunRepository runs;
    @Autowired
    @Qualifier("newsClassificationExecutor")
    private Executor executor;
    private Clock clock = Clock.systemDefaultZone();

    public int schedule(List<NewsClassificationCandidate> candidates) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<NewsClassificationCandidate> claimed = new ArrayList<NewsClassificationCandidate>();
        for (NewsClassificationCandidate candidate : candidates) {
            if (repository.claim(candidate.getItemId(), now, now.minusMinutes(RETRY_MINUTES))) {
                claimed.add(candidate);
            }
        }
        for (int start = 0; start < claimed.size(); start += BATCH_SIZE) {
            List<NewsClassificationCandidate> batch = new ArrayList<NewsClassificationCandidate>(
                    claimed.subList(start, Math.min(start + BATCH_SIZE, claimed.size())));
            try {
                executor.execute(() -> classify(batch));
            } catch (RuntimeException error) {
                for (NewsClassificationCandidate candidate : batch) {
                    repository.markFailed(candidate.getItemId(), "分类任务提交失败", modelName(), now);
                }
            }
        }
        return claimed.size();
    }

    private void classify(List<NewsClassificationCandidate> batch) {
        long started = System.currentTimeMillis();
        String input = batchInput(batch);
        try {
            List<NewsCategory> enabled = categories.findEnabled();
            if (!capabilities.isModelEnabled()) {
                for (NewsClassificationCandidate candidate : batch) {
                    repository.markRuleResult(rules.classify(candidate, enabled), LocalDateTime.now(clock));
                }
                publishClassificationChange();
                return;
            }
            Map<String, NewsClassificationAgent.Decision> decisions = agent.classify(batch, enabled);
            LocalDateTime now = LocalDateTime.now(clock);
            for (NewsClassificationCandidate candidate : batch) {
                NewsClassificationAgent.Decision decision = decisions.get(candidate.getItemId());
                if (decision == null) {
                    repository.markFailed(candidate.getItemId(), "Agent 未返回有效分类", modelName(), now);
                } else {
                    repository.markClassified(candidate.getItemId(), decision.getCategoryCode(),
                            decision.getConfidence(), decision.getReason(), modelName(), now);
                }
            }
            publishClassificationChange();
            runs.record("news-classification", "SUCCESS", input, output(decisions), null,
                    System.currentTimeMillis() - started);
        } catch (Exception ex) {
            String message = errorMessage(ex);
            LocalDateTime now = LocalDateTime.now(clock);
            for (NewsClassificationCandidate candidate : batch) {
                repository.markFailed(candidate.getItemId(), message, modelName(), now);
            }
            runs.record("news-classification", "FAILED", input, null, message,
                    System.currentTimeMillis() - started);
        }
    }

    private void publishClassificationChange() {
        try {
            revisions.invalidate("news");
        } catch (RuntimeException error) {
            log.warn("资讯分类已保存，但页面版本通知失败", error);
        }
    }

    private String batchInput(List<NewsClassificationCandidate> batch) {
        StringBuilder value = new StringBuilder("items=");
        for (NewsClassificationCandidate candidate : batch) {
            if (value.length() > 6) value.append(',');
            value.append(candidate.getItemId());
        }
        return value.toString();
    }

    private String output(Map<String, NewsClassificationAgent.Decision> decisions) {
        StringBuilder value = new StringBuilder();
        for (NewsClassificationAgent.Decision decision : decisions.values()) {
            if (value.length() > 0) value.append('\n');
            value.append(decision.getItemId()).append('=').append(decision.getCategoryCode());
        }
        return value.toString();
    }

    private String errorMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty() ? ex.getClass().getSimpleName() : message;
    }

    private String modelName() {
        if (!capabilities.isModelEnabled()) {
            return NewsRuleClassifier.VERSION;
        }
        String value = agent.modelName();
        return value == null || value.trim().isEmpty() ? "llm" : value;
    }
}
