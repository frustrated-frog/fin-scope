package com.finscope.service.quant.forecast;

import com.finscope.dao.quant.NextSessionPredictionRepository;
import com.finscope.domain.quant.data.QuantDailyBar;
import com.finscope.domain.quant.forecast.NextSessionPrediction;
import com.finscope.domain.quant.forecast.NextSessionPredictionRecord;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class NextSessionPredictionService {
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
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

    void settle(LocalDateTime now) {
        Map<String, QuantDailyBarBatch> batches = new HashMap<>();
        for (NextSessionPredictionRecord record : repository.findPending(100)) {
            NextSessionPrediction prediction = record.getPrediction();
            LocalDate target = prediction.getTargetDate();
            if (target.isAfter(now.toLocalDate())
                    || (target.equals(now.toLocalDate()) && now.toLocalTime().isBefore(LocalTime.of(15, 10)))) {
                continue;
            }
            try {
                QuantDailyBarBatch batch = batches.computeIfAbsent(record.getInstrumentCode(),
                        code -> dailyBars.fetch(code, 5000));
                settleRecord(record, batch, now);
            } catch (RuntimeException error) {
                log.warn("次日预测结果暂不可验证，id={},instrument={}", record.getId(), record.getInstrumentCode(), error);
            }
        }
    }

    private void settleRecord(NextSessionPredictionRecord record, QuantDailyBarBatch batch, LocalDateTime now) {
        NextSessionPrediction prediction = record.getPrediction();
        QuantDailyBar signal = onDate(batch, prediction.getAsOfDate());
        QuantDailyBar target = onDate(batch, prediction.getTargetDate());
        if (!validClose(signal) || !validClose(target)) {
            if (now.toLocalDate().isAfter(prediction.getTargetDate().plusDays(1))) {
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
