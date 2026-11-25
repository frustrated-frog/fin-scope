package com.finscope.service.quant.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.finscope.rpc.llm.LunaResponsesClient;
import com.finscope.rpc.quant.PythonPaperClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class PaperReviewScheduler {
    @Resource
    private PythonPaperClient paper;
    @Resource
    private PaperResearchService research;
    @Resource
    private LunaResponsesClient llm;
    @Resource(name = "paperReviewExecutor")
    private Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "${finscope.quant.paper-review-cron}", zone = "Asia/Shanghai")
    public void refresh() {
        if (!llm.isConfigured() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::reviewPending);
        } catch (RuntimeException error) {
            running.set(false);
            throw error;
        }
    }

    private void reviewPending() {
        try {
            for (JsonNode task : paper.pendingReviews()) {
                long id = task.path("id").asLong();
                String day = task.path("day").asText();
                try {
                    research.review(id, LocalDate.parse(day));
                } catch (RuntimeException error) {
                    log.warn("模拟账户 {} 的 {} 复盘未完成，下次重试", id, day, error);
                }
            }
        } finally {
            running.set(false);
        }
    }
}
