package com.finscope.service.quant.forecast;

import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.forecast.SingleStockForecast;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SingleStockForecastService {
    static final int HISTORY_LIMIT = 5000;
    static final int MINIMUM_BARS = 750;
    static final int RESEARCH_GRADE_BARS = 1500;
    static final double TRANSACTION_COST_RATE = 0.0015d;

    private final QuantDailyBarSource source;
    private final SingleStockFeatureBuilder features = new SingleStockFeatureBuilder();
    private final SingleStockWalkForwardValidator validator = new SingleStockWalkForwardValidator();

    public SingleStockForecastService(QuantDailyBarSource source) {
        this.source = source;
    }

    public SingleStockForecast forecast(String requestedCode) {
        String code = normalize(requestedCode);
        QuantDailyBarBatch batch = source.fetch(code, HISTORY_LIMIT);
        List<QuantDailyBar> bars = orderedAndValidated(code, batch.getBars());
        SingleStockForecast result = base(code, batch, bars);
        if (bars.size() < MINIMUM_BARS) {
            result.setStatus("INSUFFICIENT_DATA");
            result.setConclusion("历史日线不足 750 根，当前只能观察指标，不能生成正式概率预测。");
            result.getWarnings().add("需要更长历史覆盖才能进行滚动样本外验证");
            return result;
        }

        List<ForecastSample> samples = features.build(bars, TRANSACTION_COST_RATE);
        WalkForwardResult validation = validator.validate(samples);
        RegularizedLogisticModel model = RegularizedLogisticModel.fit(samples);
        double probability = model.predict(features.currentFeatures(bars));
        List<WalkForwardObservation> comparable = comparable(validation.getObservations(), probability);
        double[] distribution = distribution(comparable);

        result.setLabeledSampleCount(samples.size());
        result.setUpProbability(probability);
        result.setExpectedNetReturn(distribution[1]);
        result.setLowerNetReturn(distribution[0]);
        result.setUpperNetReturn(distribution[2]);
        result.setValidation(validation(validation));
        result.setRecentObservations(recent(validation.getObservations()));
        classify(result, validation, bars.size());
        result.getWarnings().add("收益基于前复权日线和固定交易成本模拟，不代表真实成交回放");
        return result;
    }

    private SingleStockForecast base(String code, QuantDailyBarBatch batch, List<QuantDailyBar> bars) {
        SingleStockForecast result = new SingleStockForecast();
        result.setInstrumentCode(code);
        result.setHorizonDays(SingleStockFeatureBuilder.HORIZON_DAYS);
        result.setBarCount(bars.size());
        result.setAsOfDate(batch.getAsOfDate());
        result.setDataFingerprint(fingerprint(bars));
        result.setSourceCode(batch.getSourceCode());
        result.setSourceFamily(batch.getSourceFamily());
        result.setQualityStatus(batch.getQualityStatus());
        result.getWarnings().addAll(batch.getWarnings());
        return result;
    }

    private void classify(SingleStockForecast result, WalkForwardResult validation, int barCount) {
        boolean edge = validation.getIndependentSampleCount() >= 12
                && validation.getBrierScore() + 0.005d < validation.getBaselineBrierScore();
        if (barCount < RESEARCH_GRADE_BARS) {
            result.setStatus("LOW_CONFIDENCE");
            result.setConclusion("模型已完成滚动验证，但历史覆盖不足六年，只能作为低置信度观察。");
        } else if (!edge) {
            result.setStatus("NO_OBSERVED_EDGE");
            result.setConclusion("样本外概率尚未稳定优于该股票自身的历史上涨率，不支持据此单独交易。");
        } else if (validation.getAccuracy() >= 0.55d && validation.getIndependentSampleCount() >= 25) {
            result.setStatus("EVIDENCE_SUPPORTED");
            result.setConclusion("样本外预测相对基础上涨率显示稳定增量，但仍需结合风险边界执行。");
        } else {
            result.setStatus("CONDITIONAL_EDGE");
            result.setConclusion("样本外概率有一定增量，证据仍具条件性，应降低仓位并继续观察。");
        }
    }

    private SingleStockForecast.Validation validation(WalkForwardResult source) {
        SingleStockForecast.Validation value = new SingleStockForecast.Validation();
        value.setOutOfSampleCount(source.getObservations().size());
        value.setIndependentSampleCount(source.getIndependentSampleCount());
        value.setAccuracy(source.getAccuracy());
        value.setBrierScore(source.getBrierScore());
        value.setBaselineBrierScore(source.getBaselineBrierScore());
        int positive = 0;
        for (WalkForwardObservation observation : source.getObservations()) if (observation.isActualPositive()) positive++;
        value.setObservedUpRate(source.getObservations().isEmpty() ? 0d : positive / (double) source.getObservations().size());
        return value;
    }

    private List<SingleStockForecast.Observation> recent(List<WalkForwardObservation> observations) {
        List<SingleStockForecast.Observation> values = new ArrayList<SingleStockForecast.Observation>();
        int from = Math.max(0, observations.size() - 12);
        for (int i = observations.size() - 1; i >= from; i--) {
            WalkForwardObservation source = observations.get(i);
            SingleStockForecast.Observation value = new SingleStockForecast.Observation();
            value.setSignalDate(source.getSignalDate());
            value.setProbability(source.getProbability());
            value.setActualNetReturn(source.getActualReturn());
            value.setCorrect(source.isCorrect());
            values.add(value);
        }
        return values;
    }

    private List<WalkForwardObservation> comparable(List<WalkForwardObservation> observations, double probability) {
        List<WalkForwardObservation> values = new ArrayList<WalkForwardObservation>();
        for (WalkForwardObservation observation : observations)
            if (Math.abs(observation.getProbability() - probability) <= 0.10d) values.add(observation);
        return values.size() >= 10 ? values : observations;
    }

    private double[] distribution(List<WalkForwardObservation> observations) {
        List<Double> values = new ArrayList<Double>();
        double sum = 0d;
        for (WalkForwardObservation observation : observations) {
            values.add(observation.getActualReturn());
            sum += observation.getActualReturn();
        }
        if (values.isEmpty()) return new double[] {0d, 0d, 0d};
        Collections.sort(values);
        return new double[] {quantile(values, 0.20d), sum / values.size(), quantile(values, 0.80d)};
    }

    private double quantile(List<Double> values, double percentile) {
        int index = (int) Math.floor((values.size() - 1) * percentile);
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private List<QuantDailyBar> orderedAndValidated(String code, List<QuantDailyBar> input) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("行情历史不能为空");
        List<QuantDailyBar> bars = new ArrayList<QuantDailyBar>(input);
        bars.sort(Comparator.comparing(QuantDailyBar::getTradeDate));
        Set<LocalDate> dates = new HashSet<LocalDate>();
        for (QuantDailyBar bar : bars) {
            if (!code.equals(bar.getInstrumentCode()) || bar.getTradeDate() == null || !dates.add(bar.getTradeDate()))
                throw new IllegalArgumentException("行情代码、日期或唯一性校验失败");
            positive(bar.getOpen()); positive(bar.getHigh()); positive(bar.getLow()); positive(bar.getClose());
            positive(bar.getAdjustedClose()); positive(bar.getVolume()); positive(bar.getAmount());
            if (bar.getHigh().compareTo(bar.getOpen().max(bar.getClose())) < 0
                    || bar.getLow().compareTo(bar.getOpen().min(bar.getClose())) > 0)
                throw new IllegalArgumentException("行情 OHLC 校验失败");
        }
        return bars;
    }

    private void positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("行情字段必须为正数");
    }

    private String normalize(String value) {
        String code = value == null ? "" : value.trim().toUpperCase();
        if (code.matches("\\d{6}\\.(SH|SZ|BJ)")) return code;
        if (!code.matches("\\d{6}")) throw new IllegalArgumentException("股票代码必须是六位 A 股代码");
        String market = code.startsWith("6") || code.startsWith("5") || code.startsWith("9") ? "SH"
                : code.startsWith("4") || code.startsWith("8") ? "BJ" : "SZ";
        return code + "." + market;
    }

    private String fingerprint(List<QuantDailyBar> bars) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (QuantDailyBar bar : bars) {
                String row = bar.getTradeDate() + "|" + bar.getInstrumentCode() + "|" + bar.getOpen()
                        + "|" + bar.getHigh() + "|" + bar.getLow() + "|" + bar.getClose()
                        + "|" + bar.getAdjustedClose() + "|" + bar.getVolume() + "|" + bar.getAmount() + "\n";
                digest.update(row.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法生成行情指纹", error);
        }
    }
}
