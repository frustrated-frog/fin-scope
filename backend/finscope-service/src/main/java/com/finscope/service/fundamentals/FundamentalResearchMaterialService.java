package com.finscope.service.fundamentals;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.fundamentals.FundamentalDimension;
import com.finscope.common.enums.investmentobservation.ReactionEventType;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.dao.investmentobservation.ReactionSampleRepository;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.fundamentals.*;
import com.finscope.domain.instrument.Instrument;
import com.finscope.domain.investmentobservation.ReactionSample;
import com.finscope.service.financials.FinancialQueryService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import com.finscope.common.enums.fundamentals.FundamentalWatchStatus;
import com.finscope.common.exception.BusinessConflictException;
import com.finscope.domain.financials.FinancialReportView;

/** 本地财报与公告投影，不触发外部抓取。 */
@Service
public class FundamentalResearchMaterialService {
    @Resource
    private FinancialReportRepository reports;
    @Resource
    private FinancialQueryService financials;
    @Resource
    private ReactionSampleRepository reactions;
    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public void populate(Instrument instrument, FundamentalResearchWorkspace result) {
        populateFinancials(instrument, result);
        result.setEvents(reactions.recentForStock(instrument.getCode(), instrument.getMarket()).stream().map(this::event).toList());
    }

    public FundamentalFinancialBaseline freeze(FundamentalResearchWorkspace workspace, String originKey) {
        if (!originKey.startsWith("FINANCIAL:")) {
            return null;
        }
        var insight = workspace.getInsights().stream().filter(value -> originKey.equals("FINANCIAL:"
                + workspace.getReportId() + ":" + value.getDimension())).findFirst()
                .orElseThrow(() -> new BusinessConflictException("财务基线已变化，请重新读取后加入验证"));
        var baseline = new FundamentalFinancialBaseline();
        baseline.setReportId(workspace.getReportId());
        baseline.setPeriodEnd(workspace.getPeriodEnd());
        baseline.setReportType(workspace.getReportType());
        baseline.setScope(workspace.getScope());
        baseline.setCurrency(workspace.getCurrency());
        baseline.setSourceCode(workspace.getSourceCode());
        baseline.setPublishedAt(workspace.getPublishedAt());
        baseline.setDimension(insight.getDimension());
        baseline.setMetrics(insight.getMetrics());
        return baseline;
    }

    public void annotateWatches(long instrumentId, List<FundamentalWatch> watches) {
        var available = available(instrumentId);
        Map<Long, FinancialReportView> views = new HashMap<>();
        var calculator = new FundamentalFinancialComparison();
        for (FundamentalWatch watch : watches) {
            watch.setDue(watch.getStatus() != FundamentalWatchStatus.CLOSED && watch.getNextReviewDate() != null
                    && !watch.getNextReviewDate().isAfter(LocalDate.now(clock)));
            var baseline = watch.getBaselineSnapshot();
            if (watch.getStatus() == FundamentalWatchStatus.CLOSED || (baseline == null && !watch.getOriginKey().startsWith("FINANCIAL:"))) {
                continue;
            }
            FinancialReport current = baseline == null ? null : available.stream().filter(report -> report.getPeriodEnd().isAfter(baseline.getPeriodEnd())
                    && report.getPeriodEnd().getMonthValue() == baseline.getPeriodEnd().getMonthValue()
                    && report.getPeriodEnd().getDayOfMonth() == baseline.getPeriodEnd().getDayOfMonth()
                    && report.getReportType().name().equals(baseline.getReportType())
                    && java.util.Objects.equals(report.getSourceCode(), baseline.getSourceCode())
                    && java.util.Objects.equals(report.getCurrency(), baseline.getCurrency())).findFirst().orElse(null);
            if (current == null && !available.isEmpty()) {
                current = available.get(0);
            }
            FundamentalFinancialBaseline snapshot = null;
            if (current != null && baseline != null) {
                var report = current;
                var view = views.computeIfAbsent(report.getId(), financials::view);
                snapshot = snapshot(report, baseline.getDimension(), view);
            }
            watch.setComparison(calculator.compare(baseline, snapshot));
            watch.getComparison().setReviewed(watch.getComparison().getToken() != null
                    && watch.getComparison().getToken().equals(watch.getReviewedComparisonToken()));
        }
    }

    private FundamentalFinancialBaseline snapshot(FinancialReport report, FundamentalDimension dimension, FinancialReportView view) {
        var workspace = new FundamentalResearchWorkspace();
        workspace.setReportId(report.getId());
        workspace.setPeriodEnd(report.getPeriodEnd());
        workspace.setReportType(report.getReportType().name());
        workspace.setScope(report.getScope());
        workspace.setCurrency(report.getCurrency());
        workspace.setSourceCode(report.getSourceCode());
        workspace.setPublishedAt(report.getPublishedAt());
        workspace.setInsights(new FundamentalResearchAnalyzer().analyze(view, null));
        return freeze(workspace, "FINANCIAL:" + report.getId() + ":" + dimension);
    }

    private List<FinancialReport> available(long instrumentId) {
        return reports.findReports(instrumentId).stream()
                .filter(report -> "CONSOLIDATED".equals(report.getScope()) && report.getPeriodEnd() != null
                        && report.getCurrency() != null && !report.getCurrency().isBlank()
                        && report.getSourceCode() != null && !report.getSourceCode().isBlank()
                        && !report.getPeriodEnd().isAfter(LocalDate.now(clock))
                        && (report.getPublishedAt() == null || !report.getPublishedAt().isAfter(LocalDateTime.now(clock)))
                        && report.getQualityStatus() != FinancialQualityStatus.CONFLICT
                        && report.getQualityStatus() != FinancialQualityStatus.UNAVAILABLE
                        && report.getQualityStatus() != FinancialQualityStatus.UNVERIFIED).toList();
    }

    private void populateFinancials(Instrument instrument, FundamentalResearchWorkspace result) {
        List<FinancialReport> available = available(instrument.getId());
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

}
