package com.finscope.service.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.dao.marketintel.CapitalFlowRepository;
import com.finscope.rpc.marketintel.CapitalReplayData;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.service.marketdata.MarketDataGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class CapitalReplayService {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime OPEN = LocalTime.of(9, 30);
    private static final LocalTime FIRST_FLOW_MINUTE = LocalTime.of(9, 31);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime CLOSE = LocalTime.of(15, 0);
    private static final int SESSION_LIMIT = 5;
    private static final int LOCAL_LOOKBACK_DAYS = 14;

    @Autowired
    private MarketIntelCapitalService capital;

    @Autowired
    private CapitalFlowRepository flowRepository;

    @Autowired
    private MarketDataGateway marketData;

    public CapitalReplayView view(Long instrumentId) {
        Instrument instrument = capital.stock(instrumentId);
        if (!List.of("SH", "SZ", "BJ").contains(instrument.getMarket())) {
            throw new IllegalArgumentException("资金复盘目前覆盖 A 股");
        }
        LocalDate today = LocalDate.now(MARKET_ZONE);
        List<CapitalFlowPoint> stored = flowRepository.findRecentMinuteVersions(
                instrumentId, today.minusDays(LOCAL_LOOKBACK_DAYS), today);
        try {
            return assemble(marketData.fetchCapitalReplay(instrument), stored);
        } catch (ProviderContractException error) {
            log.warn("资金复盘在线读取失败 instrumentId={} errorType={}", instrumentId, error.getErrorType(), error);
            CapitalReplayView fallback = assemble(new CapitalReplayData(List.of(), List.of(),
                    List.of("INTRADAY_MARKET_UNAVAILABLE", "REALTIME_FUND_FLOW_UNAVAILABLE")), stored);
            if (fallback.getSessions().isEmpty()) {
                throw error;
            }
            return fallback;
        }
    }

    static CapitalReplayView assemble(CapitalReplayData online, List<CapitalFlowPoint> stored) {
        Map<LocalDate, List<CapitalFlowPoint>> onlinePrices = byDay(online.getPrices(), CapitalReplayService::hasPrice);
        Map<LocalDate, List<CapitalFlowPoint>> onlineFlows = byDay(online.getFlows(), CapitalReplayService::hasFlow);
        Map<LocalDate, List<CapitalFlowPoint>> storedDays = byDay(stored, point -> hasPrice(point) || hasFlow(point));
        TreeSet<LocalDate> dates = new TreeSet<>(Comparator.reverseOrder());
        dates.addAll(onlinePrices.keySet());
        dates.addAll(onlineFlows.keySet());
        dates.addAll(storedDays.keySet());
        List<CapitalReplayView.Session> sessions = dates.stream().limit(SESSION_LIMIT).map(date -> {
            List<CapitalFlowPoint> local = storedDays.getOrDefault(date, List.of());
            List<CapitalFlowPoint> prices = onlinePrices.getOrDefault(date, List.of());
            if (prices.isEmpty()) {
                prices = bestLocalSource(local, CapitalReplayService::hasPrice);
            }
            List<CapitalFlowPoint> flows = onlineFlows.getOrDefault(date, List.of());
            if (flows.isEmpty()) {
                flows = bestLocalSource(local, CapitalReplayService::hasFlow).stream()
                        .filter(point -> hasStoredBaseline(point, local)).toList();
            }
            return session(date, prices, flows);
        }).toList();
        return new CapitalReplayView(sessions, online.getWarnings());
    }

    private static Map<LocalDate, List<CapitalFlowPoint>> byDay(
            List<CapitalFlowPoint> points, Predicate<CapitalFlowPoint> available) {
        return points.stream().filter(CapitalReplayService::inSession).filter(available)
                .collect(Collectors.groupingBy(point -> point.getObservedAt().toLocalDate()));
    }

    private static List<CapitalFlowPoint> bestLocalSource(
            List<CapitalFlowPoint> points, Predicate<CapitalFlowPoint> available) {
        return points.stream().filter(available).collect(Collectors.groupingBy(CapitalFlowPoint::getProviderCode))
                .entrySet().stream().max(Comparator
                        .comparingInt((Map.Entry<String, List<CapitalFlowPoint>> entry) -> entry.getValue().size())
                        .thenComparing(entry -> entry.getValue().get(entry.getValue().size() - 1).getRetrievedAt())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getValue).orElseGet(List::of);
    }

    private static CapitalReplayView.Session session(
            LocalDate date, List<CapitalFlowPoint> prices, List<CapitalFlowPoint> flows) {
        Map<LocalTime, CapitalFlowPoint> priceByTime = byMinute(prices);
        Map<LocalTime, CapitalFlowPoint> flowByTime = byMinute(flows);
        List<LocalTime> times = Stream.concat(priceByTime.keySet().stream(), flowByTime.keySet().stream())
                .distinct().sorted().toList();
        List<CapitalReplayView.Minute> minutes = new ArrayList<>(times.size());
        for (LocalTime time : times) {
            CapitalFlowPoint price = priceByTime.get(time);
            CapitalFlowPoint flow = flowByTime.get(time);
            minutes.add(new CapitalReplayView.Minute(time,
                    price == null ? null : price.getPrice(), price == null ? null : price.getIntervalTradeAmount(),
                    flow == null ? null : flow.getLargeNetInflow(), flow == null ? null : flow.getSuperLargeNetInflow()));
        }
        return new CapitalReplayView.Session(date, provider(prices), provider(flows), minutes);
    }

    private static Map<LocalTime, CapitalFlowPoint> byMinute(List<CapitalFlowPoint> points) {
        Map<LocalTime, CapitalFlowPoint> values = new HashMap<>();
        for (CapitalFlowPoint point : points) {
            values.put(point.getObservedAt().toLocalTime(), point);
        }
        return values;
    }

    private static String provider(List<CapitalFlowPoint> values) {
        return values.isEmpty() ? null : values.get(0).getProviderCode();
    }

    private static boolean inSession(CapitalFlowPoint point) {
        LocalTime time = point.getObservedAt().toLocalTime();
        return time.getSecond() == 0 && time.getNano() == 0 && !time.isBefore(OPEN) && !time.isAfter(CLOSE)
                && (!time.isAfter(MORNING_CLOSE) || !time.isBefore(AFTERNOON_OPEN));
    }

    private static boolean hasPrice(CapitalFlowPoint point) {
        return point.getPrice() != null && point.getPrice().signum() > 0;
    }

    private static boolean hasFlow(CapitalFlowPoint point) {
        return point.getLargeNetInflow() != null && point.getSuperLargeNetInflow() != null;
    }

    private static boolean hasStoredBaseline(CapitalFlowPoint point, List<CapitalFlowPoint> local) {
        LocalTime time = point.getObservedAt().toLocalTime();
        if (time.equals(FIRST_FLOW_MINUTE)) {
            return true;
        }
        LocalTime previous = time.equals(AFTERNOON_OPEN.plusMinutes(1)) ? MORNING_CLOSE : time.minusMinutes(1);
        return local.stream().anyMatch(value -> point.getProviderCode().equals(value.getProviderCode())
                && value.getObservedAt().toLocalTime().equals(previous) && hasFlow(value));
    }
}
