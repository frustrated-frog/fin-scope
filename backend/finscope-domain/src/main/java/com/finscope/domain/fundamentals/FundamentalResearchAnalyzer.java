package com.finscope.domain.fundamentals;

import com.finscope.common.enums.financials.FinancialQualityStatus;
import com.finscope.common.enums.financials.FinancialStatementType;
import com.finscope.common.enums.fundamentals.FundamentalDimension;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialReportView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 仅使用已披露、同期间口径的数值；异常是核查线索，不推断经营原因。 */
public class FundamentalResearchAnalyzer {
    public List<FundamentalInsight> analyze(FinancialReportView current, FinancialReportView previous) {
        Map<String, BigDecimal> now = values(current);
        Map<String, BigDecimal> prior = comparable(current, previous) ? values(previous) : Map.of();
        var result = new ArrayList<FundamentalInsight>();
        var growth = insight(FundamentalDimension.GROWTH, "增长来自哪里", "先核对收入和利润的变化，再验证销量、售价和产品结构。",
                "核对下期业务分部、销量、售价及产品组合，区分主营增长与一次性因素。");
        add(growth, current, "营业收入", "REVENUE", now.get("REVENUE"));
        add(growth, current, "归母净利润", "NET_PROFIT_PARENT", now.get("NET_PROFIT_PARENT"));
        BigDecimal revenueGrowth = growth(now.get("REVENUE"), prior.get("REVENUE"));
        if (revenueGrowth != null) {
            FundamentalMetric metric = metric(current, "营收同比", "REVENUE_YOY", revenueGrowth);
            metric.setUnit("%");
            growth.getMetrics().add(metric);
            growth.setConclusion("营收同比" + (revenueGrowth.signum() >= 0 ? "增加 " : "减少 ")
                    + revenueGrowth.abs().stripTrailingZeros().toPlainString() + "%；增长来源需结合业务分部验证。");
        }
        result.add(growth);

        var earnings = insight(FundamentalDimension.EARNINGS_QUALITY, "利润有没有变成现金", "对照合并净利润与经营现金流，检查回款和非持续性损益。",
                "核对期后回款、应收账龄、减值及非经常性损益，观察利润与现金差异是否持续。");
        BigDecimal profit = now.get("NET_PROFIT");
        BigDecimal cash = now.get("OPERATING_CASH_FLOW");
        add(earnings, current, "合并净利润", "NET_PROFIT", profit);
        add(earnings, current, "经营现金流", "OPERATING_CASH_FLOW", cash);
        if (profit != null && cash != null && profit.signum() > 0) {
            BigDecimal conversion = cash.divide(profit, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            var metric = metric(current, "经营现金流 / 合并净利润", "CASH_CONVERSION", conversion);
            metric.setUnit("%");
            earnings.getMetrics().add(metric);
            earnings.setAttention(cash.compareTo(profit.multiply(new BigDecimal("0.5"))) < 0);
            earnings.setConclusion(cash.signum() < 0 ? "本期利润为正，经营现金仍为净流出，优先核对营运资金与回款。"
                    : earnings.isAttention() ? "经营现金流低于合并净利润的一半，继续核对结算节奏和利润来源。"
                    : "本期经营现金流与合并净利润已有对照，持续性仍需结合后续回款与附注判断。");
        } else if (profit != null && profit.signum() <= 0) {
            earnings.setConclusion("合并净利润不为正，不计算通常意义的现金转化率；继续核对经营收支与盈利持续性。");
        } else if (profit == null) {
            earnings.setConclusion("缺少合并净利润，保留现金事实，暂不使用归母净利润替代计算现金转化率。");
        }
        result.add(earnings);

        var working = insight(FundamentalDimension.WORKING_CAPITAL, "增长占用了多少资金", "应收与存货需要结合收入规模、账龄和订单一起核查。",
                "核对客户结算、账龄、存货周转与减值，验证资金占用是否随回款和交付改善。");
        add(working, current, "应收账款", "ACCOUNTS_RECEIVABLE", now.get("ACCOUNTS_RECEIVABLE"));
        add(working, current, "存货", "INVENTORY", now.get("INVENTORY"));
        BigDecimal receivableGrowth = growth(now.get("ACCOUNTS_RECEIVABLE"), prior.get("ACCOUNTS_RECEIVABLE"));
        if (revenueGrowth != null && receivableGrowth != null && receivableGrowth.subtract(revenueGrowth).compareTo(BigDecimal.valueOf(15)) >= 0) {
            working.setAttention(true);
            working.setConclusion("应收账款增速比营收增速高出至少15个百分点，优先检查回款与结算变化。");
        }
        result.add(working);

        var allocation = insight(FundamentalDimension.CAPITAL_ALLOCATION, "资金投向与资本配置", "把经营现金、资本开支及回购等事项放在同一资金视角下核查。",
                "对照资本开支执行、现金余额与债务到期安排，跟踪新增投入和回购的实际执行。");
        BigDecimal capex = now.get("CAPITAL_EXPENDITURE");
        add(allocation, current, "资本开支", "CAPITAL_EXPENDITURE", capex);
        add(allocation, current, "货币资金", "CASH", now.get("CASH"));
        if (cash != null && capex != null && capex.signum() >= 0) {
            BigDecimal remainder = cash.subtract(capex);
            add(allocation, current, "经营现金流减资本开支", "CASH_AFTER_CAPEX", remainder);
            allocation.setAttention(remainder.signum() < 0);
            allocation.setConclusion(remainder.signum() < 0 ? "本期经营现金流减资本开支为负，后续投入与回购需要结合融资安排核查。"
                    : "本期经营现金流减资本开支为正，继续核对资金可用性与资本配置的实际执行。");
        }
        result.add(allocation);
        return result;
    }

    private Map<String, BigDecimal> values(FinancialReportView view) {
        Map<String, BigDecimal> values = new HashMap<>();
        var conflicts = new HashSet<String>();
        if (view == null || view.getReport() == null) {
            return values;
        }
        for (List<FinancialLineItem> statement : view.getStatements().values()) {
            for (FinancialLineItem item : statement) {
                String role = item.getStatementType() == FinancialStatementType.BALANCE_SHEET ? "CURRENT_PERIOD_END" : "CURRENT_YTD";
                if (!role.equals(item.getPeriodRole()) || item.getNormalizedValue() == null || item.getConceptCode() == null
                        || !Objects.equals(item.getCurrency(), view.getReport().getCurrency())
                        || !Objects.equals(item.getSourceCode(), view.getReport().getSourceCode())
                        || item.getQualityStatus() == FinancialQualityStatus.CONFLICT
                        || item.getQualityStatus() == FinancialQualityStatus.UNAVAILABLE
                        || item.getQualityStatus() == FinancialQualityStatus.UNVERIFIED) {
                    continue;
                }
                BigDecimal existing = values.putIfAbsent(item.getConceptCode(), item.getNormalizedValue());
                if (existing != null && existing.compareTo(item.getNormalizedValue()) != 0) {
                    conflicts.add(item.getConceptCode());
                }
            }
        }
        conflicts.forEach(values::remove);
        return values;
    }

    private boolean comparable(FinancialReportView current, FinancialReportView prior) {
        return current != null && prior != null && current.getReport() != null && prior.getReport() != null
                && current.getReport().getPeriodEnd().minusYears(1).equals(prior.getReport().getPeriodEnd())
                && current.getReport().getReportType() == prior.getReport().getReportType()
                && Objects.equals(current.getReport().getCurrency(), prior.getReport().getCurrency())
                && Objects.equals(current.getReport().getScope(), prior.getReport().getScope())
                && Objects.equals(current.getReport().getSourceCode(), prior.getReport().getSourceCode())
                && Objects.equals(current.getReport().getInstrumentId(), prior.getReport().getInstrumentId());
    }

    private BigDecimal growth(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() <= 0) {
            return null;
        }
        return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
    }

    private FundamentalInsight insight(FundamentalDimension dimension, String title, String conclusion, String nextCheck) {
        var insight = new FundamentalInsight();
        insight.setDimension(dimension);
        insight.setTitle(title);
        insight.setConclusion(conclusion);
        insight.setNextCheck(nextCheck);
        return insight;
    }

    private void add(FundamentalInsight insight, FinancialReportView report, String label, String concept, BigDecimal value) {
        if (value != null) {
            insight.getMetrics().add(metric(report, label, concept, value));
        }
    }

    private FundamentalMetric metric(FinancialReportView report, String label, String concept, BigDecimal value) {
        FundamentalMetric metric = new FundamentalMetric();
        metric.setLabel(label);
        metric.setConceptCode(concept);
        metric.setValue(value);
        metric.setUnit(report.getReport().getCurrency());
        metric.setReportId(report.getReport().getId());
        metric.setPeriodEnd(report.getReport().getPeriodEnd());
        return metric;
    }
}
