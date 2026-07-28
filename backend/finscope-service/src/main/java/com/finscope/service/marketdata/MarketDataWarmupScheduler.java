package com.finscope.service.marketdata;

import com.finscope.domain.instrument.SectorCategory;
import com.finscope.service.instrument.MarketIndexService;
import com.finscope.service.instrument.SectorMarketService;
import com.finscope.service.instrument.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 在交易时段预热页面高频行情，降低用户请求命中慢源和冷连接的概率。 */
@Service
public class MarketDataWarmupScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketDataWarmupScheduler.class);

    private final WatchlistService watchlist;
    private final MarketIndexService indices;
    private final SectorMarketService sectors;
    private final MarketTradingSession tradingSession;
    private final Executor executor;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public MarketDataWarmupScheduler(
            WatchlistService watchlist,
            MarketIndexService indices,
            SectorMarketService sectors,
            MarketTradingSession tradingSession,
            @Qualifier("marketDataWarmupExecutor") Executor executor,
            @Value("${finscope.market-data.warmup-enabled:false}") boolean enabled) {
        this.watchlist = watchlist;
        this.indices = indices;
        this.sectors = sectors;
        this.tradingSession = tradingSession;
        this.executor = executor;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${finscope.market-data.warmup-interval-ms:60000}")
    public void refreshHotMarketData() {
        if (!enabled || !tradingSession.isOpenNow() || !running.compareAndSet(false, true)) return;

        CompletableFuture<?>[] tasks = new CompletableFuture<?>[] {
                run("watchlist", () -> watchlist.listInvestmentItemsWithQuotes(false)),
                run("indices", () -> indices.list(false)),
                run("industry-sectors", () -> sectors.overview(SectorCategory.INDUSTRY, 5, false)),
                run("concept-sectors", () -> sectors.overview(SectorCategory.CONCEPT, 5, false))
        };
        CompletableFuture.allOf(tasks).whenComplete((ignored, error) -> running.set(false));
    }

    private CompletableFuture<Void> run(String capability, Runnable operation) {
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    operation.run();
                } catch (RuntimeException error) {
                    log.warn("Market data warmup failed for {}", capability, error);
                }
            }, executor);
        } catch (RuntimeException rejected) {
            log.warn("Market data warmup was rejected for {}", capability, rejected);
            return CompletableFuture.completedFuture(null);
        }
    }
}
