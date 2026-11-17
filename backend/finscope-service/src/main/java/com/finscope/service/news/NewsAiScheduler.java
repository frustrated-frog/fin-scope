package com.finscope.service.news;

import com.finscope.domain.radar.RadarSignal;
import com.finscope.service.cache.ViewRevisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class NewsAiScheduler {
    @Resource
    private NewsWindowService news;
    @Resource
    private NewsAiProcessor processor;
    @Resource
    private ViewRevisionService revisions;
    @Resource
    private com.finscope.dao.news.NewsAiRepository repository;
    @Resource(name = "newsAiExecutor")
    private Executor executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private java.time.LocalDate lastCleanup;

    @Scheduled(initialDelayString = "${finscope.news-ai.initial-delay-ms:20000}", fixedDelayString = "${finscope.news-ai.interval-ms:30000}")
    public void refresh() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                    boolean active = processor.isActive();
                    var signals = !active ? java.util.List.<RadarSignal>of() : news.productionSnapshot().getItems().stream().map(item -> RadarSignal.builder()
                            .itemId(item.getId()).title(item.getTitle()).content(item.getContent()).publishedAt(item.getPublishedAt())
                            .providerCode(item.getProviderCode()).sourceName(item.getSourceName()).categoryCode(item.getCategoryCode()).build()).toList();
                    processor.process(signals, now);
                    if (!now.toLocalDate().equals(lastCleanup)) {
                        repository.prune(now);
                        lastCleanup = now.toLocalDate();
                    }
                    if (active) {
                        revisions.invalidate("news");
                    }
                } catch (RuntimeException error) {
                    log.error("新闻 AI 后台处理失败，现有本地资讯继续可用", error);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException rejected) {
            running.set(false);
            log.warn("新闻 AI 后台队列繁忙，等待下轮处理 errorType={}", rejected.getClass().getSimpleName());
        }
    }
}
