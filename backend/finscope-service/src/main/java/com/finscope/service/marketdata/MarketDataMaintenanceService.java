package com.finscope.service.marketdata;

import com.finscope.dao.marketdata.MarketDataRefreshRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** 定期清理路由审计；最后成功快照不设自动过期，始终保留作灾备兜底。 */
@Service
public class MarketDataMaintenanceService {
    private static final int AUDIT_RETENTION_DAYS = 30;

    private final MarketDataRefreshRunRepository refreshRuns;
    private final Clock clock;

    @Autowired
    public MarketDataMaintenanceService(MarketDataRefreshRunRepository refreshRuns) {
        this(refreshRuns, Clock.systemDefaultZone());
    }

    MarketDataMaintenanceService(MarketDataRefreshRunRepository refreshRuns, Clock clock) {
        this.refreshRuns = refreshRuns;
        this.clock = clock;
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void cleanup() {
        refreshRuns.deleteFinishedBefore(LocalDateTime.now(clock).minusDays(AUDIT_RETENTION_DAYS));
    }
}
