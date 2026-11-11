package com.finscope.service.marketintel;

import com.finscope.dao.marketdata.MarketDataSnapshotRepository;
import com.finscope.domain.marketdata.MarketDataCapability;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.service.marketdata.MarketDataSnapshotCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 按标的、交易日保留已读取的复盘证据，临时失败不能覆盖完整分钟。 */
@Service
@Slf4j
public class CapitalReplaySnapshotService {
    @Autowired
    private MarketDataSnapshotRepository repository;

    @Autowired
    private MarketDataSnapshotCodec codec;

    // Only local reads and writes are serialized; no remote calls run under this lock.
    public synchronized CapitalReplayData retain(Long instrumentId, CapitalReplayData fresh, LocalDate today) {
        List<CapitalFlowPoint> prices = new ArrayList<>();
        List<CapitalFlowPoint> flows = new ArrayList<>();
        List<String> warnings = new ArrayList<>(fresh.getWarnings());
        for (LocalDate day = today.minusDays(14); !day.isAfter(today); day = day.plusDays(1)) {
            String key = instrumentId + ":" + day;
            CapitalReplayData current = new CapitalReplayData(onDay(fresh.getPrices(), day), onDay(fresh.getFlows(), day), List.of());
            List<CapitalFlowPoint> mergedPrices = current.getPrices();
            List<CapitalFlowPoint> mergedFlows = current.getFlows();
            try {
                CapitalReplayData saved = repository.find(MarketDataCapability.CAPITAL_REPLAY_1M, key)
                        .flatMap(codec::decodeCapitalReplay).orElse(new CapitalReplayData(List.of(), List.of(), List.of()));
                mergedPrices = merge(onDay(saved.getPrices(), day), current.getPrices(), true);
                mergedFlows = merge(onDay(saved.getFlows(), day), current.getFlows(), false);
                if (mergedPrices.stream().anyMatch(point -> !current.getPrices().contains(point))
                        || mergedFlows.stream().anyMatch(point -> !current.getFlows().contains(point))) {
                    warnings.add("CAPITAL_REPLAY_SNAPSHOT_FALLBACK");
                }
                if (!current.getPrices().isEmpty() || !current.getFlows().isEmpty()) {
                    LocalDateTime asOf = java.util.stream.Stream.concat(mergedPrices.stream(), mergedFlows.stream())
                            .map(CapitalFlowPoint::getObservedAt).max(Comparator.naturalOrder()).orElse(day.atStartOfDay());
                    repository.upsert(codec.capitalReplaySnapshot(key,
                            new CapitalReplayData(mergedPrices, mergedFlows, List.of()), asOf,
                            LocalDateTime.now(ZoneId.of("Asia/Shanghai"))));
                }
            } catch (RuntimeException error) {
                log.warn("资金复盘快照读写失败 instrumentId={} date={}", instrumentId, day, error);
                warnings.add("CAPITAL_REPLAY_SNAPSHOT_UNAVAILABLE");
            }
            prices.addAll(mergedPrices);
            flows.addAll(mergedFlows);
        }
        return new CapitalReplayData(prices, flows, new ArrayList<>(new LinkedHashSet<>(warnings)));
    }

    private static List<CapitalFlowPoint> onDay(List<CapitalFlowPoint> points, LocalDate day) {
        return points.stream().filter(point -> point.getObservedAt() != null
                && point.getObservedAt().toLocalDate().equals(day)).toList();
    }

    private static List<CapitalFlowPoint> merge(List<CapitalFlowPoint> saved, List<CapitalFlowPoint> fresh, boolean price) {
        Predicate<CapitalFlowPoint> valid = price
                ? point -> point.getPrice() != null && point.getPrice().signum() > 0
                : point -> point.getLargeNetInflow() != null && point.getSuperLargeNetInflow() != null;
        Map<String, List<CapitalFlowPoint>> providers = java.util.stream.Stream.concat(saved.stream(), fresh.stream())
                .filter(point -> point.getObservedAt() != null && point.getProviderCode() != null && valid.test(point))
                .collect(Collectors.groupingBy(CapitalFlowPoint::getProviderCode));
        return providers.entrySet().stream().map(entry -> {
            Map<LocalDateTime, CapitalFlowPoint> minutes = new LinkedHashMap<>();
            for (CapitalFlowPoint point : entry.getValue()) {
                CapitalFlowPoint previous = minutes.get(point.getObservedAt());
                if (previous == null || !price || point.getIntervalTradeAmount() != null
                        || previous.getIntervalTradeAmount() == null) {
                    minutes.put(point.getObservedAt(), point);
                }
            }
            return minutes.values().stream().sorted(Comparator.comparing(CapitalFlowPoint::getObservedAt)).toList();
        }).max(Comparator.comparingInt((List<CapitalFlowPoint> points) -> points.size())
                .thenComparing(points -> points.get(0).getProviderCode())).orElse(List.of());
    }
}
