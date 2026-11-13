package com.finscope.service.fundamentals;

import com.finscope.common.enums.fundamentals.FundamentalDimension;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.fundamentals.FundamentalResearchRepository;
import com.finscope.domain.fundamentals.CompanyBusinessNotes;
import com.finscope.domain.fundamentals.FundamentalResearchWorkspace;
import com.finscope.domain.fundamentals.FundamentalWatch;
import com.finscope.domain.fundamentals.FundamentalWatchReview;
import com.finscope.domain.fundamentals.FundamentalWatchReviewCommand;
import com.finscope.domain.instrument.Instrument;
import com.finscope.rpc.research.material.CompanyBusinessProfileClient;
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
    private FundamentalStockResolver stocks;
    @Resource
    private FundamentalResearchRepository repository;
    @Resource
    private FundamentalResearchMaterialService materials;
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
        materials.populate(instrument, result);
        result.setWatches(repository.watches(instrument.getId()));
        materials.annotateWatches(instrument.getId(), result.getWatches());
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
        watch.setBaselineSnapshot(materials.freeze(view(code), watch.getOriginKey()));
        watch.setComparison(null);
        repository.addWatch(watch);
        return view(code);
    }

    public FundamentalResearchWorkspace review(String code, long watchId, FundamentalWatchReviewCommand command) {
        Instrument instrument = requireStock(code);
        if (command.getStatus() == null || command.getNote() == null || command.getNote().isBlank()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID);
        }
        if (command.getNextReviewDate() != null && command.getNextReviewDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "下次复核日期不能早于今天");
        }
        var watches = repository.watches(instrument.getId());
        var watch = watches.stream().filter(value -> value.getId().equals(watchId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("验证事项不存在"));
        var review = new FundamentalWatchReview();
        review.setStatus(command.getStatus());
        review.setNote(command.getNote().strip());
        review.setNextReviewDate(command.getNextReviewDate() == null ? watch.getNextReviewDate() : command.getNextReviewDate());
        review.setReviewedAt(LocalDateTime.now(clock));
        if (command.getComparisonToken() != null) {
            materials.annotateWatches(instrument.getId(), watches);
            if (watch.getComparison() == null || !command.getComparisonToken().equals(watch.getComparison().getToken())) {
                throw new BusinessConflictException("财务对照已更新，请重新读取后复核");
            }
            review.setComparison(watch.getComparison());
        }
        repository.review(instrument.getId(), watchId, command.getRevision(), review);
        return view(code);
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
        for (var event : result.getEvents().stream().limit(3).toList()) {
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
        return stocks.require(code);
    }
}
