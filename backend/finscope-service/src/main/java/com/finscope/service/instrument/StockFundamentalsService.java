package com.finscope.service.instrument;

import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.instrument.StockFinancialQuarter;
import com.finscope.domain.instrument.StockFundamentals;
import com.finscope.domain.valuation.StockValuationView;
import com.finscope.rpc.marketintel.ProviderCallDeadline;
import com.finscope.service.financials.FinancialRefreshService;
import com.finscope.service.valuation.StockValuationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 自动补齐股票详情的确定性输入；远程调用不放入数据库事务。 */
@Service
@Slf4j
public class StockFundamentalsService {
    private static final int QUARTER_COUNT = 8;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(60);
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");
    @Resource
    private InstrumentRepository instruments;
    @Resource
    private FinancialRefreshService financialRefresh;
    @Resource
    private StockFinancialQuarterAssembler quarters;
    @Resource
    private StockValuationService valuations;
    @Resource
    private StockFundamentalsPeerService peers;

    private Clock clock = Clock.system(CHINA);
    // 现有 Redis 页面缓存可禁用且不提供 singleflight；本地有界门闩也缓存失败后的快照。
    private final Map<String, AttemptEntry> attempts = new LinkedHashMap<String, AttemptEntry>(16, 0.75f, true);

    public StockFundamentals load(String code, boolean refresh) {
        Instrument instrument = requireStock(code);
        LocalDate today = LocalDate.now(clock.withZone(CHINA));
        List<LocalDate> periods = recentQuarters(today);
        String key = instrument.getId() + "|" + today;
        AttemptLease lease = acquire(key);
        if (lease == null) {
            StockFundamentals local = localSnapshot(instrument, periods);
            local.getWarnings().add("股票财务刷新繁忙，本次保留本地数据");
            peers.populate(local);
            return local;
        }
        AttemptEntry entry = lease.entry;
        try {
            synchronized (entry) {
                boolean completedWhileWaiting = entry.generation != lease.generation;
                if (entry.snapshot != null && (completedWhileWaiting
                        || (!refresh && clock.instant().isBefore(entry.expiresAt)))) {
                    return entry.snapshot;
                }
                StockFundamentals snapshot = refreshSnapshot(instrument, periods, today, refresh, entry);
                entry.snapshot = snapshot;
                entry.expiresAt = clock.instant().plus(ATTEMPT_TTL);
                entry.generation++;
                return snapshot;
            }
        } finally {
            release(entry);
        }
    }

    private StockFundamentals refreshSnapshot(Instrument instrument, List<LocalDate> periods,
                                               LocalDate today, boolean refresh, AttemptEntry entry) {
        StockFundamentals result = localSnapshot(instrument, periods);
        Set<LocalDate> failedPeriods = new HashSet<LocalDate>();
        try (ProviderCallDeadline.Scope ignored = ProviderCallDeadline.open(REFRESH_TIMEOUT)) {
            refreshValuation(instrument, today, refresh, entry, result);
            List<LocalDate> required = requiredPeriods(periods);
            for (int index = required.size() - 1; index >= 0; index--) {
                LocalDate period = required.get(index);
                boolean forcePeriod = refresh && periods.contains(period);
                if (!forcePeriod && quarters.hasUsableReport(instrument.getId(), period)) {
                    continue;
                }
                if (ProviderCallDeadline.remainingMillis() == 0L) {
                    result.getWarnings().add("财务自动补齐已达到 60 秒总时限，未完成期间保留数据缺口");
                    break;
                }
                try {
                    financialRefresh.refresh(instrument.getId(), period, reportType(period));
                } catch (RuntimeException error) {
                    failedPeriods.add(period);
                    log.warn("股票财报补齐失败 instrumentId={} period={} errorType={}",
                            instrument.getId(), period, error.getClass().getSimpleName());
                    result.getWarnings().add(period + " 财报补齐失败，保留本地数据或缺口");
                }
            }
        }
        readRefreshedQuarters(result, periods, failedPeriods);
        peers.populate(result);
        result.setRefreshedAt(clock.instant());
        return result;
    }

    private void readRefreshedQuarters(StockFundamentals result, List<LocalDate> periods,
                                       Set<LocalDate> failedPeriods) {
        List<StockFinancialQuarter> previous = result.getQuarters();
        List<StockFinancialQuarter> updated;
        try {
            updated = quarters.assemble(result.getInstrument().getId(), periods);
        } catch (RuntimeException error) {
            log.warn("股票财报刷新后读取失败 instrumentId={} errorType={}",
                    result.getInstrument().getId(), error.getClass().getSimpleName());
            String gap = "刷新后财报读取失败，保留刷新前完整季度快照";
            result.getWarnings().add(gap);
            List<StockFinancialQuarter> retained = new ArrayList<StockFinancialQuarter>();
            for (StockFinancialQuarter point : previous) {
                retained.add(hasQuarterFacts(point) ? retainedQuarter(point, gap) : point);
            }
            result.setQuarters(retained);
            return;
        }
        Map<LocalDate, StockFinancialQuarter> previousByPeriod = new HashMap<LocalDate, StockFinancialQuarter>();
        for (StockFinancialQuarter point : previous) {
            if (hasQuarterFacts(point)) {
                previousByPeriod.put(point.getPeriodEnd(), point);
            }
        }
        List<StockFinancialQuarter> merged = new ArrayList<StockFinancialQuarter>();
        for (StockFinancialQuarter point : updated) {
            StockFinancialQuarter prior = previousByPeriod.get(point.getPeriodEnd());
            LocalDate priorFailure = failedPrerequisite(point.getPeriodEnd(), failedPeriods);
            if (failedPeriods.contains(point.getPeriodEnd()) && prior != null) {
                merged.add(retainedQuarter(prior, "本次财报补齐失败，保留刷新前完整季度快照"));
            } else if (prior != null && priorFailure != null && hasLostQuarterFacts(prior, point)) {
                merged.add(retainedQuarter(prior, "前序累计报告 " + priorFailure
                        + " 补齐失败且单季度数据出现缺口，保留刷新前完整季度快照"));
            } else {
                merged.add(point);
            }
        }
        result.setQuarters(merged);
    }

    private boolean hasQuarterFacts(StockFinancialQuarter point) {
        return point.getRevenue() != null || point.getNetProfitParent() != null
                || point.getOperatingCashFlow() != null || point.getGrossMarginPct() != null;
    }

    private boolean hasLostQuarterFacts(StockFinancialQuarter previous, StockFinancialQuarter current) {
        // 收入非正时毛利率本来不适用，不能仅因该比率为空而隐藏有效的新季度金额。
        return (previous.getRevenue() != null && current.getRevenue() == null)
                || (previous.getNetProfitParent() != null && current.getNetProfitParent() == null)
                || (previous.getOperatingCashFlow() != null && current.getOperatingCashFlow() == null)
                || (previous.getGrossMarginPct() != null && current.getGrossMarginPct() == null
                && current.getRevenue() != null && current.getRevenue().signum() > 0);
    }

    private LocalDate failedPrerequisite(LocalDate period, Set<LocalDate> failedPeriods) {
        if (period.getMonthValue() == 3) {
            return null;
        }
        LocalDate prior = YearMonth.from(period).minusMonths(3).atEndOfMonth();
        return failedPeriods.contains(prior) ? prior : null;
    }

    /** 整点保留证据和数值，避免把新报告元数据与旧来源金额混在一起。 */
    private StockFinancialQuarter retainedQuarter(StockFinancialQuarter source, String gap) {
        StockFinancialQuarter retained = new StockFinancialQuarter();
        retained.setPeriodEnd(source.getPeriodEnd());
        retained.setReportType(source.getReportType());
        retained.setCurrency(source.getCurrency());
        retained.setReportId(source.getReportId());
        retained.setSourceCode(source.getSourceCode());
        retained.setQualityStatus(source.getQualityStatus());
        retained.setPublishedAt(source.getPublishedAt());
        retained.setRevenue(source.getRevenue());
        retained.setNetProfitParent(source.getNetProfitParent());
        retained.setOperatingCashFlow(source.getOperatingCashFlow());
        retained.setGrossMarginPct(source.getGrossMarginPct());
        retained.setDataGaps(new ArrayList<String>(source.getDataGaps()));
        retained.getDataGaps().add(gap);
        return retained;
    }

    private void refreshValuation(Instrument instrument, LocalDate today, boolean refresh,
                                  AttemptEntry entry, StockFundamentals result) {
        StockValuationView local = result.getValuation();
        boolean observedToday = local != null && local.getLatest() != null
                && today.equals(local.getLatest().getObservedDate());
        if (!refresh && (observedToday || entry.valuationAttempted)) {
            if (!observedToday && entry.valuationWarning != null) {
                result.getWarnings().add(entry.valuationWarning);
            }
            return;
        }
        if (ProviderCallDeadline.remainingMillis() == 0L) {
            result.getWarnings().add("估值刷新未完成：已达到请求总时限");
            return;
        }
        entry.valuationAttempted = true;
        entry.valuationWarning = null;
        try {
            StockValuationView updated = valuations.refresh(instrument.getId());
            if (updated != null) {
                result.setValuation(updated);
            }
        } catch (RuntimeException error) {
            log.warn("股票估值刷新失败 instrumentId={} errorType={}",
                    instrument.getId(), error.getClass().getSimpleName());
            entry.valuationWarning = "今日估值刷新失败，保留已有真实历史快照";
            result.getWarnings().add(entry.valuationWarning);
            // refresh 可能已保存估值而在后续公司行为读取时失败，再读本地可保留成功部分。
            result.setValuation(readLocalValuation(instrument, result, local));
        }
    }

    private StockFundamentals localSnapshot(Instrument instrument, List<LocalDate> periods) {
        StockFundamentals result = new StockFundamentals();
        result.setInstrument(instrument);
        result.setQuarters(quarters.assemble(instrument.getId(), periods));
        result.setValuation(readLocalValuation(instrument, result, null));
        result.setRefreshedAt(clock.instant());
        return result;
    }

    private StockValuationView readLocalValuation(Instrument instrument, StockFundamentals result,
                                                   StockValuationView fallback) {
        try {
            return valuations.view(instrument.getId());
        } catch (RuntimeException error) {
            log.warn("股票本地估值读取失败 instrumentId={} errorType={}",
                    instrument.getId(), error.getClass().getSimpleName());
            result.getWarnings().add("本地估值暂不可用，财报趋势仍使用已有数据");
            return fallback;
        }
    }

    private Instrument requireStock(String code) {
        if (code == null || !code.trim().matches("[0-9]{6}")) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        Instrument instrument = instruments.findByCodeAndType(code.trim(), "STOCK")
                .orElseThrow(() -> new ResourceNotFoundException("股票标的不存在"));
        if (!"STOCK".equals(instrument.getType()) || !("SH".equals(instrument.getMarket())
                || "SZ".equals(instrument.getMarket()) || "BJ".equals(instrument.getMarket()))) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "仅支持 A 股股票基本面");
        }
        return instrument;
    }

    private List<LocalDate> recentQuarters(LocalDate today) {
        int quarterEndMonth = ((today.getMonthValue() - 1) / 3 + 1) * 3;
        YearMonth latest = YearMonth.of(today.getYear(), quarterEndMonth);
        if (latest.atEndOfMonth().isAfter(today)) {
            latest = latest.minusMonths(3);
        }
        List<LocalDate> result = new ArrayList<LocalDate>();
        for (int index = QUARTER_COUNT - 1; index >= 0; index--) {
            result.add(latest.minusMonths(index * 3L).atEndOfMonth());
        }
        return result;
    }

    private List<LocalDate> requiredPeriods(List<LocalDate> periods) {
        List<LocalDate> result = new ArrayList<LocalDate>();
        LocalDate first = periods.get(0);
        if (first.getMonthValue() != 3) {
            result.add(YearMonth.from(first).minusMonths(3).atEndOfMonth());
        }
        result.addAll(periods);
        return result;
    }

    private FinancialReportType reportType(LocalDate period) {
        switch (period.getMonthValue()) {
            case 3:
                return FinancialReportType.Q1;
            case 6:
                return FinancialReportType.HALF_YEAR;
            case 9:
                return FinancialReportType.Q3;
            default:
                return FinancialReportType.ANNUAL;
        }
    }

    private AttemptLease acquire(String key) {
        synchronized (attempts) {
            AttemptEntry existing = attempts.get(key);
            if (existing != null) {
                existing.users++;
                return new AttemptLease(existing, existing.generation);
            }
            if (attempts.size() >= MAX_CACHE_ENTRIES) {
                Iterator<AttemptEntry> iterator = attempts.values().iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().users == 0) {
                        iterator.remove();
                        break;
                    }
                }
            }
            if (attempts.size() >= MAX_CACHE_ENTRIES) {
                return null;
            }
            AttemptEntry created = new AttemptEntry();
            created.users = 1;
            attempts.put(key, created);
            return new AttemptLease(created, created.generation);
        }
    }

    private void release(AttemptEntry entry) {
        synchronized (attempts) {
            entry.users--;
        }
    }

    /** 仅属于该缓存实现的同步状态，不承载领域或持久化枚举语义。 */
    private static final class AttemptEntry {
        private int users;
        private volatile long generation;
        private StockFundamentals snapshot;
        private Instant expiresAt;
        private boolean valuationAttempted;
        private String valuationWarning;
    }

    private static final class AttemptLease {
        private final AttemptEntry entry;
        private final long generation;

        private AttemptLease(AttemptEntry entry, long generation) {
            this.entry = entry;
            this.generation = generation;
        }
    }
}
