package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.NextSessionPredictionRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.forecast.NextSessionPrediction;
import com.finscope.domain.quant.forecast.NextSessionPredictionRecord;
import com.finscope.domain.quant.forecast.NextSessionValidationCalculator;
import com.finscope.domain.quant.forecast.NextSessionValidationSummary;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class NextSessionPredictionService {
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    @Value("${finscope.next-session-prediction.retry-delay-minutes:15}")
    private long retryDelayMinutes = 15;
    private final Map<String, LocalDateTime> retryAfter = new HashMap<>();
    @Resource
    private NextSessionPredictionRepository repository;
    @Resource
    private QuantDailyBarSource dailyBars;

    @Scheduled(initialDelay = 60000L, fixedDelay = 60000L)
    public void synchronize() {
        try {
            repository.importFrozenReports();
            settle(LocalDateTime.now(CHINA_ZONE));
        } catch (RuntimeException error) {
            log.warn("次日预测账本同步失败，下个周期重试", error);
        }
    }

    public List<NextSessionPredictionRecord> history(String code, int limit) {
        if (code != null && !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("股票代码必须为六位数字");
        }
        repository.importFrozenReports();
        return repository.history(code, limit);
    }

    public NextSessionValidationSummary validation(String code) {
        if (code != null && !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("股票代码必须为六位数字");
        }
        List<NextSessionPredictionRecord> records = new ArrayList<>();
        long cursor = 0;
        while (true) {
            List<NextSessionPredictionRecord> page = repository.historyAfter(code, cursor);
            if (page.isEmpty()) {
                break;
            }
            records.addAll(page);
            cursor = page.get(page.size() - 1).getId();
        }
        return NextSessionValidationCalculator.summarize(records);
    }

    synchronized void settle(LocalDateTime now) {
        retryAfter.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        Map<String, QuantDailyBarBatch> batches = new HashMap<>();
        List<NextSessionPredictionRecord> pending = repository.findPending(100);
        Map<String, LocalDate> fromDates = new HashMap<>();
        for (NextSessionPredictionRecord record : pending) {
            fromDates.merge(record.getInstrumentCode(), record.getPrediction().getAsOfDate(),
                    (left, right) -> left.isBefore(right) ? left : right);
        }
        for (NextSessionPredictionRecord record : pending) {
            NextSessionPrediction prediction = record.getPrediction();
            LocalDate target = prediction.getTargetDate();
            if (target.isAfter(now.toLocalDate())
                    || (target.equals(now.toLocalDate()) && now.toLocalTime().isBefore(LocalTime.of(15, 10)))) {
                continue;
            }
            if (retryAfter.containsKey(record.getInstrumentCode())) {
                continue;
            }
            try {
                QuantDailyBarBatch batch = batches.computeIfAbsent(record.getInstrumentCode(),
                        code -> dailyBars.fetchSince(code, 5000, fromDates.get(code)));
                settleRecord(record, batch, now);
            } catch (RuntimeException error) {
                retryAfter.put(record.getInstrumentCode(), now.plusMinutes(Math.max(1, retryDelayMinutes)));
                log.warn("次日预测结果暂不可验证，id={},instrument={},retryAfter={}",
                        record.getId(), record.getInstrumentCode(), retryAfter.get(record.getInstrumentCode()), error);
            }
        }
    }

    private void settleRecord(NextSessionPredictionRecord record, QuantDailyBarBatch batch, LocalDateTime now) {
        NextSessionPrediction prediction = record.getPrediction();
        QuantDailyBar signal = onDate(batch, prediction.getAsOfDate());
        QuantDailyBar target = onDate(batch, prediction.getTargetDate());
        if (!validClose(signal) || !validClose(target)) {
            if (now.toLocalDate().isAfter(prediction.getTargetDate().plusDays(1))
                    && batch.getBars().stream().anyMatch(bar -> bar.getTradeDate().isAfter(prediction.getTargetDate()))) {
                repository.unavailable(record.getId(), now, "目标交易日或基准日缺少可验证收盘价，禁止替换为其他日期");
            }
            return;
        }
        // Re-read both prices from one QFQ vintage, avoiding ex-dividend adjustment mismatches.
        double actual = target.getClose().doubleValue() / signal.getClose().doubleValue() - 1d;
        boolean correct = (prediction.getUpProbability() >= 0.5d) == (actual > 0d);
        boolean covered = prediction.getLowerReturn() != null && prediction.getUpperReturn() != null
                && actual >= prediction.getLowerReturn() && actual <= prediction.getUpperReturn();
        repository.settle(record.getId(), actual, correct, covered, now, batch.getSourceCode());
    }

    private QuantDailyBar onDate(QuantDailyBarBatch batch, LocalDate date) {
        QuantDailyBar found = null;
        for (QuantDailyBar bar : batch.getBars()) {
            if (date.equals(bar.getTradeDate())) {
                if (found != null) {
                    throw new IllegalStateException("行情包含重复交易日，拒绝结算");
                }
                found = bar;
            }
        }
        return found;
    }

    private boolean validClose(QuantDailyBar bar) {
        return bar != null && bar.getClose() != null && bar.getClose().signum() > 0;
    }
}
