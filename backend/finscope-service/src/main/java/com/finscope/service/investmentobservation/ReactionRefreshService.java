package com.finscope.service.investmentobservation;

import com.finscope.common.enums.investmentobservation.ReactionSampleState;
import com.finscope.common.enums.investmentobservation.ReactionWindowStatus;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.investmentobservation.ReactionCalculation;
import com.finscope.domain.investmentobservation.ReactionCalculator;
import com.finscope.domain.investmentobservation.ReactionRefreshResult;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.domain.investmentobservation.ReactionWindow;
import com.finscope.rpc.marketintel.ProviderContractException;
import com.finscope.rpc.quant.QuantDailyBarBatch;
import com.finscope.rpc.quant.QuantDailyBarSource;
import com.finscope.rpc.quote.PythonTradingCalendarClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ReactionRefreshService {
    @Resource
    private ReactionSampleRepository repository;
    @Resource
    private ReactionRegistrationService registration;
    @Resource
    private QuantDailyBarSource dailyBars;
    @Resource
    private PythonTradingCalendarClient calendar;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));
    private final ReactionCalculator calculator = new ReactionCalculator();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean batchRunning = new AtomicBoolean();

    public ReactionSample refresh(long id) {
        if (!running.add(id)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该样本正在更新，请稍后重试");
        }
        try {
            ReactionSample sample = registration.require(id);
            if (sample.getState() != ReactionSampleState.OBSERVING) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "只有已确认且未归档的样本可以更新");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            try {
                ReactionCalculation result = calculate(sample, now);
                preserveCompletedWindows(sample.getCalculation(), result);
                requireUpdated(repository.saveCalculation(id, sample.getRevision(), result, now));
            } catch (ProviderContractException | IllegalArgumentException ex) {
                log.warn("reaction refresh failed sampleId={} exceptionType={}", id, ex.getClass().getSimpleName(), ex);
                String message = "行情或交易日历未满足计算口径，请检查行情服务后重试；已保留上次成功结果。";
                requireUpdated(repository.saveFailure(id, sample.getRevision(), message, now));
            }
            return registration.require(id);
        } finally {
            running.remove(id);
        }
    }

    /** 每批最多 20 项，仅处理最近 20 分钟未尝试且五日窗口未完成的样本。 */
    public ReactionRefreshResult refreshPending() {
        ReactionRefreshResult result = new ReactionRefreshResult();
        if (!batchRunning.compareAndSet(false, true)) {
            result.setBusy(true);
            return result;
        }
        try {
            List<ReactionSample> pending = repository.findDue(LocalDateTime.now(clock).minusMinutes(20), 20);
            for (ReactionSample sample : pending) {
                try {
                    ReactionSample updated = refresh(sample.getId());
                    if (updated.getRefreshError() == null) {
                        result.setRefreshed(result.getRefreshed() + 1);
                    } else {
                        result.setFailed(result.getFailed() + 1);
                    }
                } catch (BusinessException ex) {
                    if (ex.getErrorCode() != ErrorCode.BUSINESS_CONFLICT
                            && ex.getErrorCode() != ErrorCode.DATA_VERSION_CONFLICT) {
                        throw ex;
                    }
                    result.setFailed(result.getFailed() + 1);
                    log.info("reaction refresh skipped sampleId={} reason={}", sample.getId(), ex.getErrorCode());
                }
            }
            return result;
        } finally {
            batchRunning.set(false);
        }
    }

    private ReactionCalculation calculate(ReactionSample sample, LocalDateTime now) {
        LocalDate onOrAfter = sample.getPublishedAt().toLocalDate();
        if (!sample.getPublishedAt().toLocalTime().isBefore(LocalTime.of(15, 0))) {
            onOrAfter = onOrAfter.plusDays(1);
        }
        List<LocalDate> sessions = calendar.eventWindow(onOrAfter);
        QuantDailyBarBatch stock = dailyBars.fetch(sample.getInstrumentCode(), 1000);
        QuantDailyBarBatch benchmark = dailyBars.fetch("000300.SH", 1000);
        ReactionCalculation result = calculator.calculate(sample.getPublishedAt(), sessions,
                stock.getBars(), benchmark.getBars(), now);
        result.setStockSource(stock.getSourceCode());
        result.setBenchmarkSource(benchmark.getSourceCode());
        result.setStockQuality(stock.getQualityStatus());
        result.setBenchmarkQuality(benchmark.getQualityStatus());
        result.setStockAsOf(stock.getAsOfDate());
        result.setBenchmarkAsOf(benchmark.getAsOfDate());
        result.getWarnings().addAll(stock.getWarnings());
        result.getWarnings().addAll(benchmark.getWarnings());
        if (stock.isDegraded() || benchmark.isDegraded()) {
            result.getWarnings().add("行情包含备用来源或缓存，请结合行情截止日期阅读。");
        }
        result.getWarnings().add("相对表现为累计收益差，不代表事件的因果贡献。仅已登记的其他事件可作为叠加信息提示。");
        return result;
    }

    private void preserveCompletedWindows(ReactionCalculation previous, ReactionCalculation next) {
        if (previous == null) {
            return;
        }
        for (ReactionWindow window : previous.getWindows()) {
            boolean regressed = window.getStatus() == ReactionWindowStatus.READY && next.getWindows().stream()
                    .anyMatch(value -> value.getSessions() == window.getSessions()
                            && value.getStatus() != ReactionWindowStatus.READY);
            if (regressed) {
                throw new IllegalArgumentException("本次行情缺失已完成窗口的数据");
            }
        }
    }

    private void requireUpdated(boolean updated) {
        if (!updated) {
            throw new BusinessException(ErrorCode.DATA_VERSION_CONFLICT);
        }
    }
}
