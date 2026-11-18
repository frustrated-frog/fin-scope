package com.finscope.service.news;

import com.finscope.dao.news.NewsAiRepository;
import com.finscope.domain.news.ai.NewsAiStatus;
import com.finscope.rpc.llm.NewsAiLunaClient;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
public class NewsAiStatusService {
    @Resource
    private NewsAiProperties properties;
    @Resource
    private NewsAiLunaClient client;
    @Resource
    private NewsAiRepository repository;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public NewsAiStatus status() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> usage = repository.dailyUsage(now);
        NewsAiStatus status = new NewsAiStatus();
        status.setEnabled(properties.isEnabled());
        status.setConfigured(client.isConfigured());
        status.setModel(client.model());
        status.setDailyBudgetMicros(properties.getDailyBudgetMicros());
        status.setDailyMaxCalls(properties.getDailyMaxCalls());
        status.setCalls(number(usage, "calls"));
        status.setInputTokens(number(usage, "input_tokens"));
        status.setOutputTokens(number(usage, "output_tokens"));
        status.setActualMicros(number(usage, "actual_micros"));
        status.setUnresolvedMicros(number(usage, "unresolved_micros"));
        status.setRemainingMicros(properties.getDailyBudgetMicros() == null ? null
                : Math.max(0, properties.getDailyBudgetMicros() - status.getActualMicros() - status.getUnresolvedMicros()));
        status.setJobs(repository.jobCounts());
        status.setEfficiency(repository.dailyEfficiency(now));
        return status;
    }

    public java.util.List<com.finscope.domain.news.ai.NewsAiCallAudit> calls(int limit) {
        return repository.recentCalls(limit);
    }

    private long number(Map<String, Object> usage, String key) {
        return ((Number) usage.get(key)).longValue();
    }
}
