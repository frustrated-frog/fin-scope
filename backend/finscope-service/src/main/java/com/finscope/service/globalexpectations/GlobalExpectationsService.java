package com.finscope.service.globalexpectations;

import com.finscope.dao.cache.GlobalExpectationsCacheRepository;
import com.finscope.domain.globalexpectations.GlobalExpectationHistoryPoint;
import com.finscope.domain.globalexpectations.GlobalExpectationHistorySnapshot;
import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.domain.globalexpectations.GlobalExpectationPricePoint;
import com.finscope.domain.globalexpectations.GlobalExpectationsViewSnapshot;
import com.finscope.rpc.polymarket.PolymarketPricePoint;
import com.finscope.rpc.polymarket.PolymarketPublicClient;
import com.finscope.rpc.polymarket.PolymarketPublicMarket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 将 Polymarket 官方概率及变化转换为待核验的研究观察，不输出交易指令。 */
@Service
public class GlobalExpectationsService {
    private static final Logger log = LoggerFactory.getLogger(GlobalExpectationsService.class);
    private static final int MAX_MARKETS = 20;
    private static final int MAX_DISPLAY_POINTS = 96;
    private static final long FIVE_MINUTES_SECONDS = 300L;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Resource
    private PolymarketPublicClient polymarketPublicClient;
    @Resource
    private GlobalExpectationsCatalog catalog;
    @Resource
    private GlobalExpectationsCacheRepository cacheRepository;

    public List<GlobalExpectationItem> list() {
        Optional<GlobalExpectationsViewSnapshot> cached = cacheRepository.getView();
        if (cached.isPresent() && cached.get().getItems() != null && !cached.get().getItems().isEmpty()) {
            return cached.get().getItems();
        }
        return refresh();
    }

    @Scheduled(fixedDelayString = "${finscope.global-expectations.refresh-interval-ms:60000}",
            initialDelayString = "${finscope.global-expectations.refresh-initial-delay-ms:2000}")
    public void refreshScheduled() {
        refresh();
    }

    public synchronized List<GlobalExpectationItem> refresh() {
        Instant observedAt = Instant.now();
        try {
            List<MatchedMarket> markets = select(polymarketPublicClient.fetchActiveMarkets());
            if (markets.isEmpty()) {
                return staleOrUnavailable();
            }
            HistoryResult historyResult = loadHistory(markets, observedAt);
            List<GlobalExpectationItem> items = map(markets, historyResult, observedAt);
            if (items.isEmpty()) {
                return staleOrUnavailable();
            }
            GlobalExpectationsViewSnapshot snapshot = new GlobalExpectationsViewSnapshot();
            snapshot.setFetchedAt(observedAt.getEpochSecond());
            snapshot.setItems(items);
            cacheRepository.putView(snapshot);
            return items;
        } catch (Exception error) {
            log.warn("Polymarket 市场刷新失败，尝试读取 Redis 快照: error={}", error.getMessage());
            return staleOrUnavailable();
        }
    }

    private List<MatchedMarket> select(List<PolymarketPublicMarket> markets) {
        List<MatchedMarket> selected = new ArrayList<MatchedMarket>();
        for (PolymarketPublicMarket market : markets) {
            GlobalExpectationsCatalog.Definition definition = catalog.match(market.getQuestion());
            if (definition == null || market.getYesProbability() == null) {
                continue;
            }
            selected.add(new MatchedMarket(definition, market));
        }
        selected.sort(Comparator.comparing(item -> item.getMarket().getVolume(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (selected.size() > MAX_MARKETS) {
            return new ArrayList<MatchedMarket>(selected.subList(0, MAX_MARKETS));
        }
        return selected;
    }

    private HistoryResult loadHistory(List<MatchedMarket> markets, Instant observedAt) {
        List<String> tokenIds = new ArrayList<String>();
        for (MatchedMarket matched : markets) {
            String tokenId = matched.getMarket().getYesTokenId();
            if (tokenId != null && !tokenId.isBlank()) {
                tokenIds.add(tokenId);
            }
        }
        Map<String, List<PolymarketPricePoint>> remote = Collections.emptyMap();
        boolean requestSucceeded = false;
        if (!tokenIds.isEmpty()) {
            try {
                remote = polymarketPublicClient.fetchPriceHistory(tokenIds);
                requestSucceeded = true;
            } catch (Exception error) {
                log.warn("Polymarket 历史价格读取失败，尝试读取 Redis 历史: error={}", error.getMessage());
                requestSucceeded = false;
            }
        }
        Map<String, GlobalExpectationHistorySnapshot> histories = new LinkedHashMap<String, GlobalExpectationHistorySnapshot>();
        boolean complete = requestSucceeded && tokenIds.size() == markets.size();
        for (String tokenId : tokenIds) {
            List<PolymarketPricePoint> points = remote.get(tokenId);
            if (points != null && !points.isEmpty()) {
                GlobalExpectationHistorySnapshot snapshot = historySnapshot(tokenId, points, observedAt);
                histories.put(tokenId, snapshot);
                cacheRepository.putHistory(snapshot);
            } else {
                complete = false;
                cacheRepository.getHistory(tokenId).ifPresent(snapshot -> histories.put(tokenId, snapshot));
            }
        }
        return new HistoryResult(histories, complete);
    }

    private GlobalExpectationHistorySnapshot historySnapshot(String tokenId, List<PolymarketPricePoint> points,
                                                              Instant observedAt) {
        List<GlobalExpectationHistoryPoint> normalized = new ArrayList<GlobalExpectationHistoryPoint>();
        for (PolymarketPricePoint source : points) {
            GlobalExpectationHistoryPoint point = new GlobalExpectationHistoryPoint();
            point.setTimestamp(source.getTimestamp());
            point.setProbability(source.getPrice() * 100D);
            normalized.add(point);
        }
        normalized.sort(Comparator.comparingLong(GlobalExpectationHistoryPoint::getTimestamp));
        GlobalExpectationHistorySnapshot snapshot = new GlobalExpectationHistorySnapshot();
        snapshot.setTokenId(tokenId);
        snapshot.setFetchedAt(observedAt.getEpochSecond());
        snapshot.setPoints(normalized);
        return snapshot;
    }

    private List<GlobalExpectationItem> map(List<MatchedMarket> markets, HistoryResult historyResult,
                                            Instant observedAt) {
        List<GlobalExpectationItem> items = new ArrayList<GlobalExpectationItem>();
        for (MatchedMarket matched : markets) {
            PolymarketPublicMarket market = matched.getMarket();
            GlobalExpectationHistorySnapshot history = historyResult.getHistories().get(market.getYesTokenId());
            items.add(item((long) items.size() + 1L, matched.getDefinition(), market, history,
                    historyResult.isComplete(), observedAt));
        }
        return items;
    }

    private GlobalExpectationItem item(Long id, GlobalExpectationsCatalog.Definition definition,
                                       PolymarketPublicMarket market, GlobalExpectationHistorySnapshot history,
                                       boolean complete, Instant observedAt) {
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setId(id);
        item.setTheme(definition.getTheme());
        item.setQuestion(market.getQuestion());
        item.setMarketUrl(market.getMarketUrl());
        item.setProbability(market.getYesProbability());
        item.setChange5m(changeFiveMinutes(history, observedAt, market.getYesProbability()));
        item.setChange1h(toPercentagePoints(market.getOneHourPriceChange()));
        item.setChange24h(toPercentagePoints(market.getOneDayPriceChange()));
        item.setVolume(market.getVolume());
        item.setOpenInterest(market.getOpenInterest());
        item.setEndDate(market.getEndDate());
        item.setObservation(definition.getObservation());
        item.setStatus(hasSignal(item) ? "SIGNAL" : "WATCHING");
        item.setDataStatus(complete && history != null ? "LIVE" : "PARTIAL");
        item.setObservedAt(TIME_FORMATTER.format(observedAt));
        item.setLastRefreshAt(TIME_FORMATTER.format(observedAt));
        item.setPriceHistory(displayHistory(history));
        return item;
    }

    private Double changeFiveMinutes(GlobalExpectationHistorySnapshot history, Instant observedAt,
                                     Integer currentProbability) {
        if (history == null || history.getPoints() == null || currentProbability == null) {
            return null;
        }
        long target = observedAt.getEpochSecond() - FIVE_MINUTES_SECONDS;
        GlobalExpectationHistoryPoint baseline = null;
        for (GlobalExpectationHistoryPoint point : history.getPoints()) {
            if (point.getTimestamp() <= target
                    && (baseline == null || point.getTimestamp() > baseline.getTimestamp())) {
                baseline = point;
            }
        }
        return baseline == null ? null : currentProbability.doubleValue() - baseline.getProbability();
    }

    private Double toPercentagePoints(Double priceChange) {
        return priceChange == null ? null : priceChange * 100D;
    }

    private List<GlobalExpectationPricePoint> displayHistory(GlobalExpectationHistorySnapshot history) {
        if (history == null || history.getPoints() == null || history.getPoints().isEmpty()) {
            return List.of();
        }
        int step = Math.max(1, (int) Math.ceil(history.getPoints().size() / (double) MAX_DISPLAY_POINTS));
        List<GlobalExpectationPricePoint> result = new ArrayList<GlobalExpectationPricePoint>();
        for (int index = 0; index < history.getPoints().size(); index += step) {
            GlobalExpectationHistoryPoint source = history.getPoints().get(index);
            GlobalExpectationPricePoint point = new GlobalExpectationPricePoint();
            point.setObservedAt(TIME_FORMATTER.format(Instant.ofEpochSecond(source.getTimestamp())));
            point.setProbability((int) Math.round(source.getProbability()));
            result.add(point);
        }
        return result;
    }

    private boolean hasSignal(GlobalExpectationItem item) {
        return item.getChange5m() != null && Math.abs(item.getChange5m()) >= 3D
                || item.getChange1h() != null && Math.abs(item.getChange1h()) >= 5D;
    }

    private List<GlobalExpectationItem> staleOrUnavailable() {
        Optional<GlobalExpectationsViewSnapshot> cached = cacheRepository.getView();
        if (cached.isEmpty() || cached.get().getItems() == null || cached.get().getItems().isEmpty()) {
            return baseline();
        }
        String refreshedAt = TIME_FORMATTER.format(Instant.ofEpochSecond(cached.get().getFetchedAt()));
        List<GlobalExpectationItem> items = cached.get().getItems();
        for (GlobalExpectationItem item : items) {
            item.setDataStatus("STALE");
            item.setLastRefreshAt(refreshedAt);
        }
        return items;
    }

    private List<GlobalExpectationItem> baseline() {
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setId(1L);
        item.setTheme("数据状态");
        item.setQuestion("Polymarket 公共市场暂不可用，且 Redis 中没有可恢复快照");
        item.setMarketUrl("https://polymarket.com");
        item.setProbability(0);
        item.setObservation("公共数据恢复后会自动建立观察列表。");
        item.setStatus("BASELINE");
        item.setDataStatus("UNAVAILABLE");
        item.setObservedAt("等待刷新");
        item.setLastRefreshAt("—");
        item.setPriceHistory(List.of());
        return List.of(item);
    }

    private static final class MatchedMarket {
        private final GlobalExpectationsCatalog.Definition definition;
        private final PolymarketPublicMarket market;

        private MatchedMarket(GlobalExpectationsCatalog.Definition definition, PolymarketPublicMarket market) {
            this.definition = definition;
            this.market = market;
        }

        private GlobalExpectationsCatalog.Definition getDefinition() {
            return definition;
        }

        private PolymarketPublicMarket getMarket() {
            return market;
        }
    }

    private static final class HistoryResult {
        private final Map<String, GlobalExpectationHistorySnapshot> histories;
        private final boolean complete;

        private HistoryResult(Map<String, GlobalExpectationHistorySnapshot> histories, boolean complete) {
            this.histories = histories;
            this.complete = complete;
        }

        private Map<String, GlobalExpectationHistorySnapshot> getHistories() {
            return histories;
        }

        private boolean isComplete() {
            return complete;
        }
    }
}
