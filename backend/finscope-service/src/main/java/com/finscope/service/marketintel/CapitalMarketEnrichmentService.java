package com.finscope.service.marketintel;

import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import com.finscope.rpc.marketintel.tencent.TencentCapitalMarketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CapitalMarketEnrichmentService {
    private static final String QUOTE = "QUOTE";

    @Autowired
    private TencentCapitalMarketClient market;

    public List<CapitalFlowPoint> enrich(Instrument instrument, List<CapitalFlowPoint> facts) {
        List<CapitalFlowPoint> result = new ArrayList<>(facts.stream()
                .filter(point -> !QUOTE.equals(point.getGranularity())).map(point -> {
                    CapitalFlowPoint copy = new CapitalFlowPoint();
                    BeanUtils.copyProperties(point, copy);
                    return copy;
                }).toList());
        if (!("SH".equals(instrument.getMarket()) || "SZ".equals(instrument.getMarket()))) {
            return result;
        }
        try {
            Map<LocalDate, CapitalFlowPoint> history = market.fetchDailyHistory(instrument).stream()
                    .collect(Collectors.toMap(CapitalFlowPoint::getDataDate, Function.identity()));
            for (CapitalFlowPoint point : result) {
                if ("DAY_1".equals(point.getGranularity()) && history.containsKey(point.getDataDate())) {
                    fillDaily(point, history.get(point.getDataDate()));
                }
            }
        } catch (RuntimeException error) {
            log.warn("资金研究历史行情补齐失败 instrumentId={}", instrument.getId(), error);
        }
        repairLegacyMinuteContext(result);
        if (result.stream().anyMatch(point -> "MINUTE_1".equals(point.getGranularity()))) {
            try {
                Map<java.time.LocalDateTime, CapitalFlowPoint> minutes = market.fetchMinuteHistory(instrument).stream()
                        .collect(Collectors.toMap(CapitalFlowPoint::getObservedAt, Function.identity()));
                for (CapitalFlowPoint point : result) {
                    CapitalFlowPoint context = minutes.get(point.getObservedAt());
                    if ("MINUTE_1".equals(point.getGranularity()) && context != null) {
                        fillMinute(point, context);
                    }
                }
            } catch (RuntimeException error) {
                log.warn("资金研究分钟价量补齐失败 instrumentId={}", instrument.getId(), error);
            }
        }
        try {
            CapitalFlowPoint quote = market.fetchQuote(instrument);
            for (CapitalFlowPoint point : result) {
                // A post-close quote may fill the same day's closing fact; never rewrite an older day's facts.
                if ("DAY_1".equals(point.getGranularity()) && quote.getDataDate().equals(point.getDataDate())
                        && !quote.getObservedAt().toLocalTime().isBefore(LocalTime.of(15, 0))) {
                    fillClosingQuote(point, quote);
                }
            }
            result.add(quote);
        } catch (RuntimeException error) {
            log.warn("资金研究当前行情补齐失败 instrumentId={}", instrument.getId(), error);
            CapitalFlowPoint previous = latestMarketQuote(facts);
            if (previous != null) {
                result.add(previous);
            }
        }
        return result;
    }

    public static CapitalFlowPoint latestMarketQuote(List<CapitalFlowPoint> facts) {
        return facts.stream().filter(CapitalMarketEnrichmentService::isMarketQuote)
                .max(Comparator.comparing(CapitalFlowPoint::getObservedAt)).orElse(null);
    }

    public static boolean isMarketQuote(CapitalFlowPoint point) {
        return QUOTE.equals(point.getGranularity());
    }

    public static void applyMarketQuote(CapitalBehaviorMetrics metrics, List<CapitalFlowPoint> facts) {
        CapitalFlowPoint quote = latestMarketQuote(facts);
        if (quote == null) {
            return;
        }
        CapitalBehaviorMetrics.Latest latest = metrics.getLatest();
        if (latest == null) {
            latest = new CapitalBehaviorMetrics.Latest();
            metrics.setLatest(latest);
        }
        if (latest.getObservedAt() != null && quote.getObservedAt().isBefore(latest.getObservedAt())) {
            return;
        }
        latest.setTradeAmount(quote.getCumulativeTradeAmount());
        latest.setTradeVolume(quote.getTradeVolume());
        latest.setTurnoverRate(quote.getTurnoverRate());
        latest.setVolumeRatio(quote.getVolumeRatio());
        latest.setMarketObservedAt(quote.getObservedAt());
    }

    private void repairLegacyMinuteContext(List<CapitalFlowPoint> points) {
        for (CapitalFlowPoint point : points) {
            if ("EASTMONEY_DIRECT".equals(point.getProviderCode()) && "MINUTE_1".equals(point.getGranularity())
                    && point.getCalculationVersion() != null && point.getCalculationVersion().startsWith("python-market-data-v1")) {
                point.setPrice(null);
                point.setTradeVolume(null);
                point.setIntervalTradeAmount(null);
                point.setCumulativeTradeAmount(null);
                point.setQualityStatus("PARTIAL");
                point.setId(null);
                point.setCalculationVersion("python-market-data-v2");
                point.setPayloadHash(JdkFinanceHttpClient.sha256(point.getPayloadHash() + "|minute-context-v2"));
            }
        }
    }

    private void fillMinute(CapitalFlowPoint point, CapitalFlowPoint context) {
        fillDaily(point, context);
        if (point.getCumulativeTradeAmount() == null && context.getCumulativeTradeAmount() != null) {
            point.setCumulativeTradeAmount(context.getCumulativeTradeAmount());
            markEnriched(point, context);
        }
        if (point.getPrice() != null && point.getIntervalTradeAmount() != null) {
            point.setQualityStatus("COMPLETE");
        }
    }

    private void fillDaily(CapitalFlowPoint point, CapitalFlowPoint bar) {
        boolean changed = false;
        // 统一采用同日不复权行情，避免公开资金源的价格与复权 K 线混用。
        if (bar.getPrice() != null && (point.getPrice() == null || point.getPrice().compareTo(bar.getPrice()) != 0)) {
            point.setPrice(bar.getPrice());
            changed = true;
        }
        if (point.getIntervalTradeAmount() == null && bar.getIntervalTradeAmount() != null) {
            point.setIntervalTradeAmount(bar.getIntervalTradeAmount());
            changed = true;
        }
        if (point.getTradeVolume() == null && bar.getTradeVolume() != null) {
            point.setTradeVolume(bar.getTradeVolume());
            changed = true;
        }
        if (point.getTurnoverRate() == null && bar.getTurnoverRate() != null) {
            point.setTurnoverRate(bar.getTurnoverRate());
            changed = true;
        }
        if (changed) {
            markEnriched(point, bar);
        }
    }

    private void fillClosingQuote(CapitalFlowPoint point, CapitalFlowPoint quote) {
        fillDaily(point, quote);
        if (point.getVolumeRatio() == null && quote.getVolumeRatio() != null) {
            point.setVolumeRatio(quote.getVolumeRatio());
            markEnriched(point, quote);
        }
        if (point.getIntervalTradeAmount() == null && quote.getCumulativeTradeAmount() != null) {
            point.setIntervalTradeAmount(quote.getCumulativeTradeAmount());
            markEnriched(point, quote);
        }
    }

    private void markEnriched(CapitalFlowPoint point, CapitalFlowPoint context) {
        point.setId(null);
        point.setPayloadHash(JdkFinanceHttpClient.sha256(point.getPayloadHash() + "|market:" + context.getPayloadHash()));
        if (point.getCalculationVersion() == null || !point.getCalculationVersion().endsWith("|market-v1")) {
            point.setCalculationVersion(point.getCalculationVersion() + "|market-v1");
        }
        point.setRetrievedAt(context.getRetrievedAt());
        if (point.getPrice() != null && point.getIntervalTradeAmount() != null && point.getTradeVolume() != null
                && point.getTurnoverRate() != null) {
            point.setQualityStatus("COMPLETE");
        }
    }
}
