package com.finscope.service.watchlistresearch;

import com.finscope.common.enums.watchlistresearch.ResearchScanStatus;
import com.finscope.common.enums.watchlistresearch.ResearchScanStage;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.WatchlistRepository;
import com.finscope.dao.watchlistresearch.ResearchScanRepository;
import com.finscope.domain.instrument.DailyBarHistory;
import com.finscope.domain.instrument.WatchlistItem;
import com.finscope.domain.watchlistresearch.*;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WatchlistResearchScanService {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    private static final int MAX_STOCKS = 60;
    @Resource
    private ResearchScanRepository repository;
    @Resource
    private WatchlistRepository watchlist;
    @Resource
    private WatchlistResearchInputs inputs;
    @Resource(name = "watchlistResearchExecutor")
    private Executor executor;
    private Clock clock = Clock.system(CHINA);

    @PostConstruct
    public void recoverInterrupted() {
        repository.recoverInterrupted();
    }

    public ResearchScanView view(boolean refresh) {
        List<WatchlistItem> stocks = stocks();
        String key = membershipKey(stocks);
        LocalDate date = scanDate();
        ResearchScanRun attempt = repository.latestAttempt(key, date).orElse(null);
        if (!stocks.isEmpty() && (refresh || attempt == null) && !repository.active().isPresent()) {
            start(stocks, key, date, refresh);
        }
        ResearchScanView result = new ResearchScanView();
        result.setActive(repository.active().orElse(null));
        result.setLatest(repository.latestCompleted(key).orElse(null));
        result.setLastAttempt(repository.latestAttempt(key, date).orElse(null));
        result.setHistory(repository.history());
        return result;
    }

    public ResearchScanRun history(String id) {
        return repository.find(id).orElseThrow(() -> new ResourceNotFoundException("扫描记录不存在"));
    }

    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Shanghai")
    public void dailyScan() {
        try {
            view(false);
        } catch (RuntimeException error) {
            log.error("自选研究定时触发失败", error);
        }
    }

    private void start(List<WatchlistItem> stocks, String key, LocalDate date, boolean refresh) {
        ResearchScanRun run = new ResearchScanRun();
        run.setId(UUID.randomUUID().toString());
        run.setMembershipKey(key);
        run.setScanDate(date);
        run.setStatus(ResearchScanStatus.RUNNING);
        run.setTotalStocks(stocks.size());
        run.setStartedAt(clock.instant());
        run.setMessage("正在对齐股票与沪深300的日收益");
        if (!repository.tryStart(run, !refresh)) {
            return;
        }
        try {
            executor.execute(() -> execute(run, stocks, refresh));
        } catch (RuntimeException error) {
            fail(run, "扫描队列繁忙，请稍后重新扫描", error);
        }
    }

    private void execute(ResearchScanRun run, List<WatchlistItem> allStocks, boolean refresh) {
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofSeconds(180))) {
            WatchlistResearchSnapshot result = new WatchlistResearchSnapshot();
            result.setTotalStocks(allStocks.size());
            List<WatchlistItem> selected = allStocks.subList(0, Math.min(MAX_STOCKS, allStocks.size()));
            if (selected.size() < allStocks.size()) {
                result.getGaps().add("本次扫描前60只股票，其余" + (allStocks.size() - selected.size()) + "只未纳入");
            }
            analyzeMarket(run, selected, result, refresh);
            analyzeFinancials(run, selected, result, refresh);
            result.setObservedAt(clock.instant());
            result.setFindings(WatchlistResearchRules.findings(result));
            markNewFindings(run.getMembershipKey(), result);
            run.setSnapshot(result);
            boolean partial = result.getMarketCovered() < allStocks.size()
                    || result.getFinancialCovered() < allStocks.size() || !result.getGaps().isEmpty()
                    || result.getStrengths().stream().anyMatch(stock -> !stock.getGaps().isEmpty())
                    || result.getGrowthQuality().stream().anyMatch(stock -> !stock.getGaps().isEmpty());
            boolean hasResult = result.getMarketCovered() > 0 || !result.getRelationships().isEmpty()
                    || result.getGrowthQuality().stream().anyMatch(quality -> quality.getRevenueTtm() != null
                    || quality.getProfitTtm() != null || quality.getOperatingCashFlowTtm() != null);
            run.setStatus(hasResult ? (partial ? ResearchScanStatus.PARTIAL : ResearchScanStatus.READY)
                    : ResearchScanStatus.FAILED);
            run.setFinishedAt(clock.instant());
            run.setMessage(hasResult ? "扫描完成：" + result.getFindings().size() + "项研究观察"
                    : "本次没有取得可计算的数据，已保留此前结果，可重新扫描");
            repository.finish(run);
            log.info("自选研究扫描完成 runId={} status={} stocks={} marketCovered={} financialCovered={}",
                    run.getId(), run.getStatus(), allStocks.size(), result.getMarketCovered(), result.getFinancialCovered());
        } catch (LinkageError error) {
            fail(run, "后端运行版本不一致，重启后可重新扫描", error);
        } catch (RuntimeException error) {
            fail(run, "扫描未完成，已保留此前结果，可重新扫描", error);
        }
    }

    private void analyzeMarket(ResearchScanRun run, List<WatchlistItem> stocks,
                                 WatchlistResearchSnapshot result, boolean refresh) {
        Map<String, DailyBarHistory> histories = new LinkedHashMap<String, DailyBarHistory>();
        int processed = 0;
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofSeconds(90))) {
            DailyBarHistory benchmark = inputs.market("000300", "SH", refresh);
            if (!benchmark.getBars().isEmpty()) {
                result.setMarketDate(benchmark.getBars().get(benchmark.getBars().size() - 1).getTradeDate());
            }
            for (WatchlistItem stock : stocks) {
                checkInterrupted();
                if (ProviderCallDeadline.remainingMillis() == 0L) {
                    result.getGaps().add("行情阶段达到90秒时限，未完成股票留待下次扫描");
                    break;
                }
                try {
                    DailyBarHistory history = inputs.market(stock.getCode(), stock.getMarket(), refresh);
                    histories.put(stock.getCode(), history);
                    StockStrengthAnalysis strength = MarketResearchCalculator.strength(stock, history, benchmark);
                    result.getStrengths().add(strength);
                    if (strength.getExcessReturnPp() != null) {
                        result.setMarketCovered(result.getMarketCovered() + 1);
                    }
                } catch (RuntimeException error) {
                    result.getGaps().add(stock.getName() + "：行情暂不可用");
                    log.warn("自选研究行情跳过 runId={} code={} errorType={}", run.getId(), stock.getCode(),
                            error.getClass().getSimpleName());
                }
                processed++;
                repository.progress(run.getId(), ResearchScanStage.MARKET, processed, "正在核对行情：" + stock.getName());
            }
            result.setRelationships(MarketResearchCalculator.relationships(stocks, histories, benchmark));
            result.getStrengths().sort(Comparator.comparing(StockStrengthAnalysis::getExcessReturnPp,
                    Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(StockStrengthAnalysis::getCode));
        } catch (RuntimeException error) {
            result.getGaps().add("沪深300基准暂不可用，本次未完成市场关系计算");
            log.warn("自选研究基准不可用 runId={} errorType={}", run.getId(), error.getClass().getSimpleName());
        }
    }

    private void analyzeFinancials(ResearchScanRun run, List<WatchlistItem> stocks,
                                     WatchlistResearchSnapshot result, boolean refresh) {
        int processed = 0;
        for (WatchlistItem stock : stocks) {
            checkInterrupted();
            if (ProviderCallDeadline.remainingMillis() == 0L) {
                result.getGaps().add("扫描达到180秒总时限，剩余财报下次补齐");
                break;
            }
            repository.progress(run.getId(), ResearchScanStage.FINANCIAL, processed, "正在核对增长质量：" + stock.getName());
            try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(Duration.ofSeconds(15))) {
                StockGrowthQuality quality = GrowthQualityCalculator.analyze(stock,
                        inputs.financials(stock.getCode(), refresh).getQuarters(), LocalDate.now(clock));
                result.getGrowthQuality().add(quality);
                if (quality.getRevenueTtm() != null && quality.getProfitTtm() != null
                        && quality.getOperatingCashFlowTtm() != null) {
                    result.setFinancialCovered(result.getFinancialCovered() + 1);
                }
            } catch (RuntimeException error) {
                result.getGaps().add(stock.getName() + "：财报暂不可用");
                log.warn("自选研究财报跳过 runId={} code={} errorType={}", run.getId(), stock.getCode(),
                        error.getClass().getSimpleName());
            }
            processed++;
        }
        run.setProcessedStocks(processed);
    }

    private void markNewFindings(String key, WatchlistResearchSnapshot current) {
        Set<String> previous = repository.latestCompleted(key).map(ResearchScanRun::getSnapshot)
                .map(snapshot -> snapshot.getFindings().stream().map(ResearchFinding::getKey).collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
        for (ResearchFinding finding : current.getFindings()) {
            finding.setNewlyObserved(!previous.contains(finding.getKey()));
        }
    }

    private void fail(ResearchScanRun run, String message, Throwable error) {
        log.error("自选研究扫描失败 runId={}", run.getId(), error);
        run.setStatus(ResearchScanStatus.FAILED);
        run.setSnapshot(null);
        run.setFinishedAt(clock.instant());
        run.setMessage(message);
        repository.finish(run);
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("扫描被应用关闭中断");
        }
    }

    private List<WatchlistItem> stocks() {
        List<WatchlistItem> result = new ArrayList<WatchlistItem>(watchlist.findByTypes(Collections.singletonList("STOCK")));
        result.sort(Comparator.comparing(WatchlistItem::getCode));
        return result;
    }

    private String membershipKey(List<WatchlistItem> stocks) {
        String members = stocks.stream().map(stock -> stock.getInstrumentId() + ":" + stock.getCode()
                + ":" + stock.getMarket()).collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((WatchlistResearchSnapshot.METHOD_VERSION + "|" + members).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256不可用", error);
        }
    }

    private LocalDate scanDate() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(CHINA));
        LocalDate date = now.toLocalDate();
        if (now.toLocalTime().isBefore(LocalTime.of(16, 30))) {
            date = date.minusDays(1);
        }
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date;
    }
}
