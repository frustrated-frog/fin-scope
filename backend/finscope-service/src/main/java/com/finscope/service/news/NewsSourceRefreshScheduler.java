package com.finscope.service.news;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 以固定周期预热来源快照；单飞保护位于 NewsSourceRefreshService。 */
@Service
public class NewsSourceRefreshScheduler {
    private final NewsSourceRefreshService refresh;

    public NewsSourceRefreshScheduler(NewsSourceRefreshService refresh) {
        this.refresh = refresh;
    }

    @Scheduled(initialDelayString = "${finscope.news.refresh-initial-delay-ms:1000}",
            fixedDelayString = "${finscope.news.refresh-interval-ms:30000}")
    public void refresh() {
        refresh.requestRefresh();
    }
}
