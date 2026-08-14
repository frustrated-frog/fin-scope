package com.finscope.service.globalexpectations;

import com.finscope.domain.globalexpectations.GlobalExpectationItem;
import com.finscope.domain.globalexpectations.GlobalExpectationPricePoint;
import com.finscope.rpc.polymarket.PolymarketPublicClient;
import com.finscope.rpc.polymarket.PolymarketPublicMarket;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/** 将公开预测市场价格转换为待核验的研究观察，不输出交易指令。 */
@Service
public class GlobalExpectationsService {
    private static final int MAX_MARKETS = 20;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Resource
    private PolymarketPublicClient polymarketPublicClient;
    @Resource
    private GlobalExpectationsCatalog catalog;
    @Resource
    private GlobalExpectationSnapshotCache snapshotCache;
    private volatile List<GlobalExpectationItem> latest = Collections.emptyList();
    private volatile Instant lastSuccessfulRefresh;

    public List<GlobalExpectationItem> list() {
        if (latest.isEmpty()) {
            return refresh();
        }
        return latest;
    }

    @Scheduled(fixedDelayString = "${finscope.global-expectations.refresh-interval-ms:60000}",
            initialDelayString = "${finscope.global-expectations.refresh-initial-delay-ms:2000}")
    public void refreshScheduled() {
        refresh();
    }

    public synchronized List<GlobalExpectationItem> refresh() {
        Instant observedAt = Instant.now();
        try {
            List<GlobalExpectationItem> items = map(polymarketPublicClient.fetchActiveMarkets(), observedAt);
            if (!items.isEmpty()) {
                latest = List.copyOf(items);
                lastSuccessfulRefresh = observedAt;
                return items;
            }
        } catch (Exception ignored) {
            // 公共源短暂不可用时保留最近一次成功读取，页面不会因此中断。
        }
        if (!latest.isEmpty()) {
            return stale(latest);
        }
        return baseline();
    }

    private List<GlobalExpectationItem> map(List<PolymarketPublicMarket> markets, Instant observedAt) {
        List<GlobalExpectationItem> items = new ArrayList<GlobalExpectationItem>();
        for (PolymarketPublicMarket market : markets) {
            GlobalExpectationsCatalog.Definition definition = catalog.match(market.getQuestion());
            if (definition == null || market.getYesProbability() == null) {
                continue;
            }
            String marketKey = market.getMarketId().isBlank() ? market.getMarketUrl() : market.getMarketId();
            items.add(item((long) items.size() + 1, definition, market, marketKey, observedAt));
        }
        items.sort(Comparator.comparing(GlobalExpectationItem::getVolume,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (items.size() > MAX_MARKETS) {
            return new ArrayList<GlobalExpectationItem>(items.subList(0, MAX_MARKETS));
        }
        return items;
    }

    private GlobalExpectationItem item(Long id, GlobalExpectationsCatalog.Definition definition,
                                       PolymarketPublicMarket market, String marketKey, Instant observedAt) {
        Integer probability = market.getYesProbability();
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setId(id);
        item.setTheme(definition.getTheme());
        item.setQuestion(market.getQuestion());
        item.setMarketUrl(market.getMarketUrl());
        item.setProbability(probability);
        item.setChange5m(snapshotCache.changeSince(marketKey, observedAt, probability, Duration.ofMinutes(5)));
        item.setChange1h(snapshotCache.changeSince(marketKey, observedAt, probability, Duration.ofHours(1)));
        item.setChange24h(snapshotCache.changeSince(marketKey, observedAt, probability, Duration.ofHours(24)));
        item.setVolume(market.getVolume());
        item.setOpenInterest(market.getOpenInterest());
        item.setEndDate(market.getEndDate());
        item.setObservation(definition.getObservation());
        item.setStatus(hasSignal(item) ? "SIGNAL" : "WATCHING");
        item.setDataStatus("LIVE");
        item.setObservedAt(TIME_FORMATTER.format(observedAt));
        item.setLastRefreshAt(TIME_FORMATTER.format(observedAt));
        snapshotCache.record(marketKey, observedAt, probability);
        item.setPriceHistory(history(marketKey));
        return item;
    }

    private boolean hasSignal(GlobalExpectationItem item) {
        return item.getChange5m() != null && Math.abs(item.getChange5m()) >= 3D
                || item.getChange1h() != null && Math.abs(item.getChange1h()) >= 5D;
    }

    private List<GlobalExpectationPricePoint> history(String marketKey) {
        List<GlobalExpectationPricePoint> points = new ArrayList<GlobalExpectationPricePoint>();
        for (GlobalExpectationSnapshotCache.Snapshot snapshot : snapshotCache.history(marketKey)) {
            GlobalExpectationPricePoint point = new GlobalExpectationPricePoint();
            point.setObservedAt(TIME_FORMATTER.format(snapshot.getObservedAt()));
            point.setProbability(snapshot.getProbability());
            points.add(point);
        }
        return points;
    }

    private List<GlobalExpectationItem> stale(List<GlobalExpectationItem> cached) {
        List<GlobalExpectationItem> items = new ArrayList<GlobalExpectationItem>();
        for (GlobalExpectationItem source : cached) {
            source.setDataStatus("STALE");
            source.setLastRefreshAt(lastSuccessfulRefresh == null ? "—" : TIME_FORMATTER.format(lastSuccessfulRefresh));
            items.add(source);
        }
        return items;
    }

    private List<GlobalExpectationItem> baseline() {
        List<GlobalExpectationItem> items = new ArrayList<GlobalExpectationItem>();
        GlobalExpectationItem item = new GlobalExpectationItem();
        item.setId(1L);
        item.setTheme("科技供应链");
        item.setQuestion("公共市场连接暂不可用：等待下一次 Polymarket 快照");
        item.setMarketUrl("https://polymarket.com");
        item.setProbability(0);
        item.setObservation("公共市场恢复后会开始积累本轮观察快照。");
        item.setStatus("BASELINE");
        item.setDataStatus("UNAVAILABLE");
        item.setPriceHistory(List.of());
        items.add(item);
        item.setObservedAt("等待刷新");
        return items;
    }
}
