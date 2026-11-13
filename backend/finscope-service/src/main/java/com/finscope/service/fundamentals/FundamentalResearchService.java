package com.finscope.service.fundamentals;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.fundamentals.FundamentalDimension;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.fundamentals.FundamentalResearchRepository;
import com.finscope.dao.instrument.InstrumentRepository;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.fundamentals.CompanyBusinessNotes;
import com.finscope.domain.fundamentals.FundamentalEvent;
import com.finscope.domain.fundamentals.FundamentalResearchAnalyzer;
import com.finscope.domain.fundamentals.FundamentalResearchWorkspace;
import com.finscope.domain.fundamentals.FundamentalWatch;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.rpc.research.material.CompanyBusinessProfileClient;
import com.finscope.service.financials.FinancialQueryService;
import com.finscope.service.instrument.StockFundamentalsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 基本面研究的本地投影与用户复核；财报补齐和公司资料抓取仅由显式刷新触发。 */
@Service
@Slf4j
public class FundamentalResearchService {
    @Resource
    private InstrumentRepository instruments;
    @Resource
    private FinancialReportRepository reports;
    @Resource
    private FinancialQueryService financials;
    @Resource
    private FundamentalResearchRepository repository;
    @Resource
    private ReactionSampleRepository reactions;
    @Resource
    private CompanyBusinessProfileClient profiles;
    @Resource
    private StockFundamentalsService fundamentals;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public FundamentalResearchWorkspace view(String code) {
        Instrument instrument = requireStock(code);
        var result = new FundamentalResearchWorkspace();
        result.setCode(instrument.getCode());
        result.setName(instrument.getName());
        result.setAssembledAt(LocalDateTime.now(clock));
        result.setProfile(repository.profile(instrument.getId()).orElse(null));
        result.setNotes(repository.notes(instrument.getId()));
        populateFinancials(instrument, result);
        result.setEvents(reactions.recentForStock(code, instrument.getMarket()).stream().map(this::event).toList());
        result.setWatches(repository.watches(instrument.getId()));
        populateSuggestions(result);
        long attention = result.getInsights().stream().filter(value -> value.isAttention()).count();
        result.setSummary(result.getReportId() == null ? "先补齐公司经营资料与财报，建立可持续核对的基本面底稿。"
                : attention > 0 ? "本期有 " + attention + " 项财务变化值得优先核对，结合公告跟踪资金投入与经营兑现。"
                : "从增长来源、利润现金兑现和资本配置三个方向，持续核对公司的经营变化。");
        return result;
    }

    public FundamentalResearchWorkspace refresh(String code) {
        Instrument instrument = requireStock(code);
        String profileNotice = null;
        String financialNotice = null;
        List<String> gaps = new ArrayList<>();
        try {
            repository.saveProfile(profiles.fetch(instrument));
        } catch (RuntimeException error) {
            logRefreshFailure(code, "company-profile", error);
            profileNotice = "公司资料本次未更新，已有经营底稿继续保留。";
        }
        try {
            var refreshed = fundamentals.loadQuartersForResearch(code, true);
            if (refreshed != null) {
                gaps.addAll(refreshed.getWarnings().stream().distinct().limit(4).toList());
            }
        } catch (RuntimeException error) {
            logRefreshFailure(code, "financials", error);
            financialNotice = "财报本次未补齐，已有报告与验证记录继续保留。";
        }
        var result = view(code);
        result.getNotices().addAll(gaps);
        if (profileNotice != null) {
            result.getNotices().add(profileNotice);
        }
        if (financialNotice != null) {
            result.getNotices().add(financialNotice);
        }
        return result;
    }

    public FundamentalResearchWorkspace saveNotes(String code, CompanyBusinessNotes notes) {
        Instrument instrument = requireStock(code);
        notes.setInstrumentId(instrument.getId());
        notes.setUpdatedAt(LocalDateTime.now(clock));
        repository.saveNotes(notes);
        return view(code);
    }

    public FundamentalResearchWorkspace addWatch(String code, FundamentalWatch watch) {
        Instrument instrument = requireStock(code);
        var saved = repository.watches(instrument.getId());
        if (saved.stream().anyMatch(value -> value.getOriginKey().equals(watch.getOriginKey()))) {
            return view(code);
        }
        if (saved.stream().filter(value -> value.getStatus() != FundamentalWatchStatus.CLOSED).count() >= 100) {
            throw new BusinessConflictException("该公司的进行中验证已达100条，请先结束已有验证");
        }
        watch.setInstrumentId(instrument.getId());
        watch.setCreatedAt(LocalDateTime.now(clock));
        watch.setStatus(FundamentalWatchStatus.TRACKING);
        watch.setRevision(0);
        repository.addWatch(watch);
        return view(code);
    }

    public FundamentalResearchWorkspace review(String code, long watchId, int revision, FundamentalWatchStatus status, String note) {
        Instrument instrument = requireStock(code);
        repository.review(instrument.getId(), watchId, revision, status, note, LocalDateTime.now(clock));
        return view(code);
    }

    private void populateFinancials(Instrument instrument, FundamentalResearchWorkspace result) {
        List<FinancialReport> available = reports.findReports(instrument.getId()).stream()
                .filter(report -> "CONSOLIDATED".equals(report.getScope())
                        && !report.getPeriodEnd().isAfter(LocalDate.now(clock))
                        && (report.getPublishedAt() == null || !report.getPublishedAt().isAfter(LocalDateTime.now(clock)))
                        && report.getQualityStatus() != FinancialQualityStatus.CONFLICT
                        && report.getQualityStatus() != FinancialQualityStatus.UNAVAILABLE
                        && report.getQualityStatus() != FinancialQualityStatus.UNVERIFIED).toList();
        if (available.isEmpty()) {
            return;
        }
        FinancialReport latest = available.get(0);
        FinancialReport previous = available.stream().filter(report -> report.getPeriodEnd().equals(latest.getPeriodEnd().minusYears(1))
                && report.getReportType() == latest.getReportType()
                && java.util.Objects.equals(report.getCurrency(), latest.getCurrency())
                && java.util.Objects.equals(report.getSourceCode(), latest.getSourceCode())).findFirst().orElse(null);
        result.setReportId(latest.getId());
        result.setPeriodEnd(latest.getPeriodEnd());
        result.setReportType(latest.getReportType().name());
        result.setScope(latest.getScope());
        result.setCurrency(latest.getCurrency());
        result.setSourceCode(latest.getSourceCode());
        result.setPublishedAt(latest.getPublishedAt());
        result.setInsights(new FundamentalResearchAnalyzer().analyze(financials.view(latest.getId()),
                previous == null ? null : financials.view(previous.getId())));
    }

    private FundamentalEvent event(ReactionSample sample) {
        var result = new FundamentalEvent();
        result.setSampleId(sample.getId());
        result.setTitle(sample.getTitle());
        result.setSubtype(sample.getEventSubtype().name());
        result.setPublishedAt(sample.getPublishedAt());
        result.setSourceUrl(sample.getSourceUrl());
        if (sample.getEventType() == ReactionEventType.REPURCHASE) {
            result.setDimension(FundamentalDimension.CAPITAL_ALLOCATION);
            switch (sample.getEventSubtype()) {
                case REPURCHASE_FINANCING -> {
                    result.setImpact("已披露回购融资支持；承诺额度不等于实际借款，继续核对签约、提款与资金成本。");
                    result.setNextCheck("核对正式贷款合同、实际提款、利率和已执行回购金额。");
                }
                case REPURCHASE_SHAREHOLDERS -> {
                    result.setImpact("补充回购事项中的股东结构事实，不能据此推断资金流入或回购已经执行。");
                    result.setNextCheck("核对实际回购进展、资金支出与后续股本安排。");
                }
                default -> {
                    result.setImpact("影响资本配置与股本安排，对照经营现金、资本开支及回购的实际执行。");
                    result.setNextCheck("核对回购用途、已执行金额、现金支出和后续股本变化。");
                }
            }
        } else if (sample.getEventType() == ReactionEventType.CONTRACT) {
            result.setDimension(FundamentalDimension.GROWTH);
            result.setImpact("新增订单或合同阶段，后续核对交付、收入确认与回款节奏。");
            result.setNextCheck("核对合同最新状态、交付周期、收入确认和实际回款。");
        } else {
            result.setDimension(FundamentalDimension.EARNINGS_QUALITY);
            result.setImpact("更新盈利事实或预测，后续对照正式财报及经营现金兑现。");
            result.setNextCheck("核对预测口径、正式财报及非经常性损益和回款情况。");
        }
        return result;
    }

    private void populateSuggestions(FundamentalResearchWorkspace result) {
        Set<String> saved = result.getWatches().stream().map(FundamentalWatch::getOriginKey).collect(java.util.stream.Collectors.toSet());
        for (var insight : result.getInsights()) {
            if (insight.getMetrics().isEmpty()) {
                continue;
            }
            String baseline = result.getPeriodEnd() + " · 合并口径 · 报告 #" + result.getReportId() + " · " + result.getSourceCode() + "；"
                    + insight.getMetrics().stream().map(metric -> metric.getLabel() + " " + amount(metric.getValue(), metric.getUnit()))
                    .collect(java.util.stream.Collectors.joining("；"));
            FundamentalWatch watch = suggestion("FINANCIAL:" + result.getReportId() + ":" + insight.getDimension(), insight.getTitle(),
                    insight.getConclusion(), baseline, insight.getNextCheck());
            if (!saved.contains(watch.getOriginKey())) {
                result.getSuggestions().add(watch);
            }
        }
        for (FundamentalEvent event : result.getEvents().stream().limit(3).toList()) {
            FundamentalWatch watch = suggestion("EVENT:" + event.getSampleId(), event.getTitle(), event.getImpact(),
                    "已披露事项：" + event.getTitle() + "；公开时间 " + event.getPublishedAt(), event.getNextCheck());
            if (!saved.contains(watch.getOriginKey())) {
                result.getSuggestions().add(watch);
            }
        }
    }

    private FundamentalWatch suggestion(String key, String title, String hypothesis, String baseline, String nextCheck) {
        var watch = new FundamentalWatch();
        watch.setOriginKey(key);
        watch.setTitle(title);
        watch.setHypothesis(hypothesis);
        watch.setBaseline(baseline);
        watch.setSupportCondition(nextCheck + " 将新披露与以上基线对照，记录支持当前认识的事实。");
        watch.setChallengeCondition("若执行、回款或经营数据与以上认识背离，记录具体反证并重新评估。");
        watch.setNextReviewDate(LocalDate.now(clock).plusMonths(1));
        return watch;
    }

    private String amount(BigDecimal value, String unit) {
        if ("%".equals(unit)) {
            return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
        }
        String currency = "CNY".equals(unit) ? "元" : " " + unit;
        BigDecimal divisor = value.abs().compareTo(new BigDecimal("100000000")) >= 0 ? new BigDecimal("100000000")
                : value.abs().compareTo(new BigDecimal("10000")) >= 0 ? new BigDecimal("10000") : BigDecimal.ONE;
        String scale = divisor.compareTo(new BigDecimal("100000000")) == 0 ? "亿" : divisor.compareTo(BigDecimal.ONE) > 0 ? "万" : "";
        return value.divide(divisor, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + scale + currency;
    }

    private void logRefreshFailure(String code, String stage, RuntimeException error) {
        // 保留调用栈用于定位；不记录上游异常消息或 cause，避免供应商 URL 中的凭据进入日志。
        RuntimeException diagnostic = new RuntimeException(error.getClass().getSimpleName());
        diagnostic.setStackTrace(error.getStackTrace());
        log.warn("基本面底稿更新失败 code={} stage={} errorType={}", code, stage, error.getClass().getSimpleName(), diagnostic);
    }

    private Instrument requireStock(String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        Instrument instrument = instruments.findByCodeAndType(code, "STOCK")
                .orElseThrow(() -> new ResourceNotFoundException("股票标的不存在"));
        if (!Set.of("SH", "SZ", "BJ").contains(instrument.getMarket())) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "基本面研究当前支持 A 股股票");
        }
        return instrument;
    }
}
