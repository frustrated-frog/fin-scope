package com.finscope.service.instrument;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialReportType;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.dao.financials.FinancialReportRepository;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialReport;
import com.finscope.domain.instrument.StockFinancialQuarter;
import com.finscope.service.financials.QuarterDerivationEngine;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 从本地合并报表的累计原值组装单季数据，不依赖预先派生的指标或 AI 解读。 */
@Component
public class StockFinancialQuarterAssembler {
    private static final String CONSOLIDATED = "CONSOLIDATED";
    private static final String CURRENT_YTD = "CURRENT_YTD";
    private static final String REVENUE = "REVENUE";
    private static final String NET_PROFIT_PARENT = "NET_PROFIT_PARENT";
    private static final String OPERATING_CASH_FLOW = "OPERATING_CASH_FLOW";
    private static final String OPERATING_COST = "OPERATING_COST";
    private static final String COST_OF_REVENUE = "COST_OF_REVENUE";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Resource
    private FinancialReportRepository reports;
    @Resource
    private QuarterDerivationEngine quarterDerivation;

    public List<StockFinancialQuarter> assemble(Long instrumentId, List<LocalDate> periods) {
        requireInstrument(instrumentId);
        if (periods == null || periods.isEmpty()) {
            return new ArrayList<StockFinancialQuarter>();
        }
        Set<LocalDate> requiredPeriods = new HashSet<LocalDate>();
        for (LocalDate period : periods) {
            reportType(period);
            requiredPeriods.add(period);
            LocalDate priorPeriod = priorPeriod(period);
            if (priorPeriod != null) {
                requiredPeriods.add(priorPeriod);
            }
        }
        Map<LocalDate, FinancialReport> selected = selectReports(instrumentId, requiredPeriods);
        Map<Long, Map<String, BigDecimal>> cumulativeByReport = new HashMap<Long, Map<String, BigDecimal>>();
        List<StockFinancialQuarter> result = new ArrayList<StockFinancialQuarter>();
        for (LocalDate period : periods) {
            result.add(assembleQuarter(period, selected, cumulativeByReport));
        }
        return result;
    }

    /** 部分原始累计行已可用时保留本地数据，避免因缺少分析结果而反复请求上游。 */
    public boolean hasUsableReport(Long instrumentId, LocalDate period) {
        requireInstrument(instrumentId);
        FinancialReportType type = reportType(period);
        FinancialReport report = reports.findReport(instrumentId, period, type, CONSOLIDATED).orElse(null);
        if (report == null || !Objects.equals(instrumentId, report.getInstrumentId())
                || !Objects.equals(period, report.getPeriodEnd()) || report.getReportType() != type
                || !CONSOLIDATED.equals(report.getScope()) || reportProblem(report) != null) {
            return false;
        }
        return !loadCumulative(report).isEmpty();
    }

    private Map<LocalDate, FinancialReport> selectReports(Long instrumentId, Set<LocalDate> requiredPeriods) {
        Map<LocalDate, FinancialReport> selected = new HashMap<LocalDate, FinancialReport>();
        for (FinancialReport report : reports.findReports(instrumentId)) {
            if (!Objects.equals(instrumentId, report.getInstrumentId()) || report.getId() == null
                    || !CONSOLIDATED.equals(report.getScope())
                    || !requiredPeriods.contains(report.getPeriodEnd())
                    || report.getReportType() != reportType(report.getPeriodEnd())) {
                continue;
            }
            FinancialReport previous = selected.get(report.getPeriodEnd());
            if (previous == null || report.getId() > previous.getId()) {
                selected.put(report.getPeriodEnd(), report);
            }
        }
        return selected;
    }

    private StockFinancialQuarter assembleQuarter(LocalDate period, Map<LocalDate, FinancialReport> selected,
                                                   Map<Long, Map<String, BigDecimal>> cumulativeByReport) {
        StockFinancialQuarter point = new StockFinancialQuarter();
        point.setPeriodEnd(period);
        point.setReportType(reportType(period));
        FinancialReport current = selected.get(period);
        if (current == null) {
            point.setQualityStatus(FinancialQualityStatus.UNAVAILABLE);
            point.getDataGaps().add("缺少" + period + "合并财报，无法生成单季度数据");
            return point;
        }
        point.setReportId(current.getId());
        point.setSourceCode(current.getSourceCode());
        point.setCurrency(current.getCurrency());
        point.setQualityStatus(current.getQualityStatus());
        point.setPublishedAt(current.getPublishedAt());
        if (current.getWarningMessage() != null && !current.getWarningMessage().isBlank()) {
            point.getDataGaps().add(current.getWarningMessage());
        }
        String problem = reportProblem(current);
        if (problem != null) {
            point.getDataGaps().add(period + "财报" + problem);
            return point;
        }
        Map<String, BigDecimal> currentValues = cumulativeByReport.computeIfAbsent(current.getId(),
                ignored -> loadCumulative(current));
        LocalDate priorPeriod = priorPeriod(period);
        Map<String, BigDecimal> priorValues = new HashMap<String, BigDecimal>();
        if (priorPeriod != null) {
            FinancialReport prior = selected.get(priorPeriod);
            String priorProblem = priorProblem(current, prior, priorPeriod);
            if (priorProblem != null) {
                point.getDataGaps().add(priorProblem);
                return point;
            }
            priorValues = cumulativeByReport.computeIfAbsent(prior.getId(), ignored -> loadCumulative(prior));
        }
        point.setRevenue(deriveQuarter(REVENUE, "营业收入", period, currentValues, priorValues, point));
        point.setNetProfitParent(deriveQuarter(NET_PROFIT_PARENT, "归母净利润", period,
                currentValues, priorValues, point));
        point.setOperatingCashFlow(deriveQuarter(OPERATING_CASH_FLOW, "经营活动现金流净额", period,
                currentValues, priorValues, point));
        BigDecimal cost = deriveQuarter(OPERATING_COST, "营业成本", period, currentValues, priorValues, point);
        if (point.getRevenue() == null || cost == null) {
            point.getDataGaps().add("毛利率：缺少可用的单季度营业收入或营业成本");
        } else if (point.getRevenue().signum() <= 0) {
            point.getDataGaps().add("毛利率：单季度营业收入不大于0，无法计算");
        } else {
            point.setGrossMarginPct(point.getRevenue().subtract(cost).multiply(HUNDRED)
                    .divide(point.getRevenue(), 4, RoundingMode.HALF_UP));
        }
        return point;
    }

    private String priorProblem(FinancialReport current, FinancialReport prior, LocalDate priorPeriod) {
        if (prior == null) {
            return "缺少" + priorPeriod + "前序合并累计财报，无法派生单季度数据";
        }
        String problem = reportProblem(prior);
        if (problem != null) {
            return priorPeriod + "前序财报" + problem + "，无法派生单季度数据";
        }
        if (!Objects.equals(current.getSourceCode(), prior.getSourceCode())
                || !Objects.equals(current.getCurrency(), prior.getCurrency())
                || !Objects.equals(current.getScope(), prior.getScope())) {
            return priorPeriod + "前序财报的来源、币种或合并口径与当前报告不一致，无法派生单季度数据";
        }
        return null;
    }

    private BigDecimal deriveQuarter(String concept, String label, LocalDate period,
                                     Map<String, BigDecimal> current, Map<String, BigDecimal> prior,
                                     StockFinancialQuarter point) {
        BigDecimal cumulative = current.get(concept);
        if (cumulative == null) {
            point.getDataGaps().add(label + "：" + period + "缺少可用的同来源同币种累计值");
            return null;
        }
        LocalDate priorPeriod = priorPeriod(period);
        if (priorPeriod == null) {
            return cumulative;
        }
        BigDecimal priorCumulative = prior.get(concept);
        if (priorCumulative == null) {
            point.getDataGaps().add(label + "：" + priorPeriod + "缺少可用的同来源同币种前序累计值");
        }
        return quarterDerivation.singleQuarter(cumulative, priorCumulative);
    }

    private Map<String, BigDecimal> loadCumulative(FinancialReport report) {
        Map<String, FinancialLineItem> selected = new HashMap<String, FinancialLineItem>();
        for (FinancialLineItem item : reports.findAllLineItems(report.getId())) {
            if (!isUsableLine(report, item)) {
                continue;
            }
            FinancialLineItem previous = selected.get(item.getConceptCode());
            if (previous == null || (item.getId() != null
                    && (previous.getId() == null || item.getId() > previous.getId()))) {
                selected.put(item.getConceptCode(), item);
            }
        }
        Map<String, BigDecimal> values = new HashMap<String, BigDecimal>();
        for (Map.Entry<String, FinancialLineItem> entry : selected.entrySet()) {
            // normalizedValue 已归一化；unitMultiplier 仅保留来源信息，不参与再次换算。
            values.put(entry.getKey(), entry.getValue().getNormalizedValue());
        }
        if (!values.containsKey(OPERATING_COST) && values.containsKey(COST_OF_REVENUE)) {
            values.put(OPERATING_COST, values.get(COST_OF_REVENUE));
        }
        return values;
    }

    private boolean isUsableLine(FinancialReport report, FinancialLineItem item) {
        if (item == null || !Objects.equals(report.getId(), item.getReportId())
                || !CURRENT_YTD.equals(item.getPeriodRole()) || item.getNormalizedValue() == null
                || !Objects.equals(report.getSourceCode(), item.getSourceCode())
                || !Objects.equals(report.getCurrency(), item.getCurrency())
                || isRejectedQuality(item.getQualityStatus())) {
            return false;
        }
        if (OPERATING_CASH_FLOW.equals(item.getConceptCode())) {
            return item.getStatementType() == FinancialStatementType.CASH_FLOW;
        }
        return (REVENUE.equals(item.getConceptCode()) || NET_PROFIT_PARENT.equals(item.getConceptCode())
                || OPERATING_COST.equals(item.getConceptCode()) || COST_OF_REVENUE.equals(item.getConceptCode()))
                && item.getStatementType() == FinancialStatementType.INCOME;
    }

    private String reportProblem(FinancialReport report) {
        if (report.getId() == null) {
            return "缺少报告标识";
        }
        if (isRejectedQuality(report.getQualityStatus())) {
            return "质量状态为" + report.getQualityStatus() + "，累计值不可用";
        }
        if (report.getSourceCode() == null || report.getSourceCode().isBlank()
                || report.getCurrency() == null || report.getCurrency().isBlank()) {
            return "缺少来源或币种，无法校验累计值口径";
        }
        return null;
    }

    private boolean isRejectedQuality(FinancialQualityStatus quality) {
        return quality == FinancialQualityStatus.CONFLICT || quality == FinancialQualityStatus.UNAVAILABLE;
    }

    private LocalDate priorPeriod(LocalDate period) {
        if (period.getMonthValue() == 3) {
            return null;
        }
        LocalDate prior = period.minusMonths(3);
        return prior.withDayOfMonth(prior.lengthOfMonth());
    }

    private FinancialReportType reportType(LocalDate period) {
        if (period == null || period.getDayOfMonth() != period.lengthOfMonth()) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "财报日期必须为自然季度末");
        }
        return switch (period.getMonthValue()) {
            case 3 -> FinancialReportType.Q1;
            case 6 -> FinancialReportType.HALF_YEAR;
            case 9 -> FinancialReportType.Q3;
            case 12 -> FinancialReportType.ANNUAL;
            default -> throw new BusinessException(ErrorCode.REQUEST_PARAMETER_INVALID, "财报日期必须为自然季度末");
        };
    }

    private void requireInstrument(Long instrumentId) {
        if (instrumentId == null) {
            throw new BusinessException(ErrorCode.REQUEST_PARAMETER_MISSING);
        }
    }
}
