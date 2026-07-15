package com.finscope.service.marketintel;

import com.finscope.domain.marketintel.CapitalBehaviorEvaluation;
import com.finscope.domain.marketintel.CapitalBehaviorSignal;
import com.finscope.domain.marketintel.CapitalBehaviorSnapshot;
import com.finscope.domain.marketintel.CapitalFlowPoint;
import com.finscope.domain.marketintel.CapitalHistoryQuality;
import com.finscope.domain.marketintel.CapitalSignalEvaluation;
import com.finscope.rpc.marketintel.JdkFinanceHttpClient;
import com.finscope.service.marketintel.factor.CapitalFactorRegistry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对资金行为信号执行严格前缀回放的事件研究。事件触发只读取当日及以前的事实，
 * 未来价格仅用于计算标签。
 */
@Service
public class CapitalFactorEvaluationService {
    public static final int MINIMUM_PUBLISHABLE_SAMPLES = 5;
    private static final int[] HORIZONS = new int[]{1, 3, 5};
    private static final int SCALE = 6;

    private final CapitalBehaviorSignalService signals;
    private final CapitalHistoryQualityGate historyQualityGate;

    public CapitalFactorEvaluationService(CapitalBehaviorSignalService signals,
                                          CapitalHistoryQualityGate historyQualityGate) {
        this.signals = signals;
        this.historyQualityGate = historyQualityGate;
    }

    public CapitalBehaviorEvaluation evaluate(CapitalBehaviorSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("capital snapshot is required");
        List<CapitalFlowPoint> daily = daily(snapshot.getFacts());
        LocalDateTime asOf = snapshot.getAsOf();
        LocalDate qualityDate = asOf == null ? (daily.isEmpty() ? LocalDate.now()
                : daily.get(daily.size() - 1).getDataDate()) : asOf.toLocalDate();
        CapitalHistoryQuality historyQuality = historyQualityGate.evaluate(snapshot.getFacts(), qualityDate);
        Map<Integer, List<BigDecimal>> baselines = baselines(daily);
        Map<String, Bucket> buckets = new LinkedHashMap<String, Bucket>();
        int evaluable = 0;
        int missing = 0;
        for (int eventIndex = 0; eventIndex < daily.size(); eventIndex++) {
            List<CapitalFlowPoint> prefix = Collections.unmodifiableList(
                    new ArrayList<CapitalFlowPoint>(daily.subList(0, eventIndex + 1)));
            List<CapitalBehaviorSignal> eventSignals = signals.detect(prefix);
            for (CapitalBehaviorSignal signal : eventSignals) {
                for (int horizon : HORIZONS) {
                    String key = signal.getType() + "|" + horizon;
                    Bucket bucket = buckets.computeIfAbsent(key,
                            ignored -> new Bucket(signal.getType(), label(signal), horizon));
                    bucket.lastEventDate = daily.get(eventIndex).getDataDate();
                    if (eventIndex + horizon >= daily.size()) continue;
                    evaluable++;
                    Outcome outcome = outcome(daily, eventIndex, horizon);
                    if (outcome == null) {
                        missing++;
                    } else {
                        bucket.outcomes.add(outcome);
                    }
                }
            }
        }

        List<String> gaps = new ArrayList<String>(historyQuality.getDataGaps());
        if (missing > 0) gaps.add("有 " + missing + " 个事件的价格标签缺失，未纳入历史统计。");
        if (buckets.isEmpty()) gaps.add("当前日线窗口内没有已成熟的资金行为事件样本。");

        int completed = evaluable - missing;
        BigDecimal coverage = rate(completed, evaluable);
        BigDecimal missingRate = rate(missing, evaluable);
        boolean reliable = historyQuality.isReliable();
        if (missingRate.compareTo(new BigDecimal("0.100000")) > 0) {
            reliable = false;
            gaps.add("未来价格标签缺失率超过 10%，本次不发布历史收益统计。");
        }
        List<CapitalSignalEvaluation> evaluations = new ArrayList<CapitalSignalEvaluation>();
        for (Bucket bucket : buckets.values()) {
            CapitalSignalEvaluation value = summarize(bucket, baselines.get(bucket.horizonDays), reliable);
            evaluations.add(value);
            if (reliable && !value.eligibleForAgent()) {
                gaps.add(value.getSignalLabel() + " " + value.getHorizonDays() + " 日样本仅 "
                        + value.getSampleCount() + " 次，未展示百分比。");
            }
        }
        applyDecay(evaluations);
        boolean available = reliable && evaluations.stream().anyMatch(CapitalSignalEvaluation::eligibleForAgent);
        LocalDate dataFrom = daily.isEmpty() ? null : daily.get(0).getDataDate();
        LocalDate dataTo = daily.isEmpty() ? null : daily.get(daily.size() - 1).getDataDate();
        CapitalBehaviorEvaluation result = CapitalBehaviorEvaluation.of(snapshot.getInstrumentId(), snapshot.getId(), asOf,
                dataFrom, dataTo, CapitalFactorRegistry.VERSION, CapitalBehaviorSignalService.VERSION,
                fingerprint(snapshot, daily), !reliable ? "DATA_UNRELIABLE"
                        : available ? "AVAILABLE" : "INSUFFICIENT_DATA",
                daily.size(), evaluable, coverage, missingRate, evaluations, gaps);
        result.setHistoryQualityStatus(reliable ? "RELIABLE" : "DATA_UNRELIABLE");
        result.setPriceCoverageRate(historyQuality.getPriceCoverageRate());
        result.setAmountCoverageRate(historyQuality.getAmountCoverageRate());
        return result;
    }

    private Map<Integer, List<BigDecimal>> baselines(List<CapitalFlowPoint> daily) {
        Map<Integer, List<BigDecimal>> values = new LinkedHashMap<Integer, List<BigDecimal>>();
        for (int horizon : HORIZONS) {
            List<BigDecimal> returns = new ArrayList<BigDecimal>();
            for (int eventIndex = 0; eventIndex + horizon < daily.size(); eventIndex++) {
                Outcome outcome = outcome(daily, eventIndex, horizon);
                if (outcome != null) returns.add(outcome.forwardReturn);
            }
            values.put(horizon, returns);
        }
        return values;
    }

    private List<CapitalFlowPoint> daily(List<CapitalFlowPoint> facts) {
        Map<LocalDateTime, CapitalFlowPoint> distinct = new LinkedHashMap<LocalDateTime, CapitalFlowPoint>();
        List<CapitalFlowPoint> sorted = new ArrayList<CapitalFlowPoint>();
        if (facts != null) {
            for (CapitalFlowPoint point : facts) {
                if (point != null && "DAY_1".equals(point.getGranularity()) && point.getObservedAt() != null) {
                    sorted.add(point);
                }
            }
        }
        sorted.sort(Comparator.comparing(CapitalFlowPoint::getObservedAt)
                .thenComparing(item -> item.getId() == null ? Long.MIN_VALUE : item.getId()));
        for (CapitalFlowPoint point : sorted) distinct.put(point.getObservedAt(), point);
        return Collections.unmodifiableList(new ArrayList<CapitalFlowPoint>(distinct.values()));
    }

    private Outcome outcome(List<CapitalFlowPoint> daily, int eventIndex, int horizon) {
        BigDecimal entry = positivePrice(daily.get(eventIndex));
        if (entry == null) return null;
        List<BigDecimal> pathReturns = new ArrayList<BigDecimal>();
        for (int index = eventIndex + 1; index <= eventIndex + horizon; index++) {
            BigDecimal price = positivePrice(daily.get(index));
            if (price == null) return null;
            pathReturns.add(relativeReturn(entry, price));
        }
        BigDecimal forwardReturn = pathReturns.get(pathReturns.size() - 1);
        BigDecimal mfe = pathReturns.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal mae = pathReturns.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        return new Outcome(forwardReturn, mfe, mae);
    }

    private BigDecimal positivePrice(CapitalFlowPoint point) {
        return point.getPrice() != null && point.getPrice().signum() > 0 ? point.getPrice() : null;
    }

    private BigDecimal relativeReturn(BigDecimal entry, BigDecimal exit) {
        return exit.divide(entry, SCALE + 4, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private CapitalSignalEvaluation summarize(Bucket bucket, List<BigDecimal> baselineReturns,
                                               boolean publishableHistory) {
        int size = bucket.outcomes.size();
        if (!publishableHistory || size < MINIMUM_PUBLISHABLE_SAMPLES
                || baselineReturns == null || baselineReturns.isEmpty()) {
            return CapitalSignalEvaluation.insufficient(bucket.signalType, bucket.signalLabel,
                    bucket.horizonDays, size, bucket.lastEventDate);
        }
        List<BigDecimal> returns = new ArrayList<BigDecimal>();
        List<BigDecimal> mfes = new ArrayList<BigDecimal>();
        List<BigDecimal> maes = new ArrayList<BigDecimal>();
        int positive = 0;
        for (Outcome outcome : bucket.outcomes) {
            returns.add(outcome.forwardReturn);
            mfes.add(outcome.mfe);
            maes.add(outcome.mae);
            if (outcome.forwardReturn.signum() > 0) positive++;
        }
        CapitalSignalEvaluation value = new CapitalSignalEvaluation();
        value.setSignalType(bucket.signalType);
        value.setSignalLabel(bucket.signalLabel);
        value.setHorizonDays(bucket.horizonDays);
        value.setSampleCount(size);
        value.setAverageReturn(average(returns));
        value.setMedianReturn(median(returns));
        value.setPositiveRate(rate(positive, size));
        value.setAverageMfe(average(mfes));
        value.setAverageMae(average(maes));
        BigDecimal baselineAverage = average(baselineReturns);
        BigDecimal baselineMedian = median(baselineReturns);
        value.setBaselineAverageReturn(baselineAverage);
        value.setBaselineMedianReturn(baselineMedian);
        value.setExcessAverageReturn(value.getAverageReturn().subtract(baselineAverage).setScale(SCALE, RoundingMode.HALF_UP));
        value.setExcessMedianReturn(value.getMedianReturn().subtract(baselineMedian).setScale(SCALE, RoundingMode.HALF_UP));
        List<BigDecimal> excessReturns = new ArrayList<BigDecimal>();
        for (BigDecimal item : returns) excessReturns.add(item.subtract(baselineAverage));
        value.setStabilityStatus(stability(excessReturns));
        value.setDecayStatus("INSUFFICIENT_SAMPLE");
        value.setEvaluationStatus("EXPLORATORY");
        value.setLastEventDate(bucket.lastEventDate);
        return value;
    }

    private void applyDecay(List<CapitalSignalEvaluation> evaluations) {
        Map<String, List<CapitalSignalEvaluation>> bySignal = new LinkedHashMap<String, List<CapitalSignalEvaluation>>();
        for (CapitalSignalEvaluation value : evaluations) {
            bySignal.computeIfAbsent(value.getSignalType(), ignored -> new ArrayList<CapitalSignalEvaluation>())
                    .add(value);
        }
        for (List<CapitalSignalEvaluation> values : bySignal.values()) {
            values.sort(Comparator.comparingInt(CapitalSignalEvaluation::getHorizonDays));
            CapitalSignalEvaluation previous = null;
            for (CapitalSignalEvaluation value : values) {
                if (value.getExcessAverageReturn() == null) {
                    value.setDecayStatus("INSUFFICIENT_SAMPLE");
                } else if (previous == null || previous.getExcessAverageReturn() == null) {
                    value.setDecayStatus("BASELINE");
                } else if (value.getExcessAverageReturn().signum()
                        != previous.getExcessAverageReturn().signum()) {
                    value.setDecayStatus("REVERSING");
                } else if (value.getExcessAverageReturn().abs().compareTo(
                        previous.getExcessAverageReturn().abs().multiply(new BigDecimal("0.80"))) < 0) {
                    value.setDecayStatus("DECAYING");
                } else {
                    value.setDecayStatus("PERSISTENT");
                }
                previous = value;
            }
        }
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(values.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<BigDecimal>(values);
        sorted.sort(BigDecimal::compareTo);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle).setScale(SCALE, RoundingMode.HALF_UP);
        return sorted.get(middle - 1).add(sorted.get(middle))
                .divide(new BigDecimal("2"), SCALE, RoundingMode.HALF_UP);
    }

    private String stability(List<BigDecimal> returns) {
        if (returns.size() < 10) return "INSUFFICIENT_SAMPLE";
        int middle = returns.size() / 2;
        int firstSign = average(returns.subList(0, middle)).signum();
        int secondSign = average(returns.subList(middle, returns.size())).signum();
        return firstSign != 0 && firstSign == secondSign ? "CONSISTENT" : "MIXED";
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), SCALE, RoundingMode.HALF_UP);
    }

    private String fingerprint(CapitalBehaviorSnapshot snapshot, List<CapitalFlowPoint> daily) {
        StringBuilder canonical = new StringBuilder(CapitalBehaviorEvaluation.VERSION)
                .append('|').append(CapitalHistoryQualityGate.VERSION)
                .append('|').append(CapitalFactorRegistry.VERSION)
                .append('|').append(CapitalBehaviorSignalService.VERSION)
                .append('|').append(snapshot.getInstrumentId())
                .append('|').append(snapshot.getId());
        for (CapitalFlowPoint point : daily) {
            canonical.append('|').append(point.getId()).append(':').append(point.getObservedAt())
                    .append(':').append(point.getPrice()).append(':').append(point.getMainNetInflow())
                    .append(':').append(point.getIntervalTradeAmount()).append(':')
                    .append(point.getCalculationVersion()).append(':').append(point.getPayloadHash());
        }
        return JdkFinanceHttpClient.sha256(canonical.toString());
    }

    private String label(CapitalBehaviorSignal signal) {
        return signal.getLabel() == null || signal.getLabel().trim().isEmpty()
                ? signal.getType() : signal.getLabel();
    }

    private static final class Bucket {
        private final String signalType;
        private final String signalLabel;
        private final int horizonDays;
        private final List<Outcome> outcomes = new ArrayList<Outcome>();
        private LocalDate lastEventDate;

        private Bucket(String signalType, String signalLabel, int horizonDays) {
            this.signalType = signalType;
            this.signalLabel = signalLabel;
            this.horizonDays = horizonDays;
        }
    }

    private static final class Outcome {
        private final BigDecimal forwardReturn;
        private final BigDecimal mfe;
        private final BigDecimal mae;

        private Outcome(BigDecimal forwardReturn, BigDecimal mfe, BigDecimal mae) {
            this.forwardReturn = forwardReturn;
            this.mfe = mfe;
            this.mae = mae;
        }
    }
}
