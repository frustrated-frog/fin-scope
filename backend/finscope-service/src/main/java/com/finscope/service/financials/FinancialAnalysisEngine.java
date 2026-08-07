package com.finscope.service.financials;

import com.finscope.domain.financials.FinancialFinding;
import com.finscope.domain.financials.FinancialLineItem;
import com.finscope.domain.financials.FinancialMetric;
import com.finscope.domain.financials.FinancialQualityStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinancialAnalysisEngine {
    public static final String FORMULA_VERSION = "financial-metrics-v2";
    private static final String RULE_VERSION = "financial-rules-v2";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public FinancialAnalysisResult analyze(List<FinancialLineItem> current,
                                           List<FinancialLineItem> priorYear) {
        Map<String, BigDecimal> now = values(current);
        Map<String, BigDecimal> prior = values(priorYear);
        FinancialAnalysisResult result = new FinancialAnalysisResult();
        String currency = currency(current);

        BigDecimal revenue = now.get("REVENUE");
        BigDecimal priorRevenue = prior.get("REVENUE");
        BigDecimal revenueYoy = percentageChange(revenue, priorRevenue);
        if (revenueYoy != null) {
            result.getMetrics().add(metric("REVENUE_YOY", "营业收入同比", revenueYoy, "%"));
        } else {
            result.getDataGaps().add("缺少上年同期营业收入，无法计算营收同比");
        }

        BigDecimal cost = first(now, "OPERATING_COST", "TOTAL_OPERATING_COST");
        BigDecimal grossMargin = ratio(subtract(revenue, cost), revenue);
        if (grossMargin != null) {
            result.getMetrics().add(metric("GROSS_MARGIN", "毛利率", grossMargin, "%"));
        }

        BigDecimal priorCost = first(prior, "OPERATING_COST", "TOTAL_OPERATING_COST");
        BigDecimal priorGrossMargin = ratio(subtract(priorRevenue, priorCost), priorRevenue);
        if (grossMargin != null && priorGrossMargin != null) {
            result.getMetrics().add(metric("GROSS_MARGIN_YOY_CHANGE", "毛利率同比变化",
                    grossMargin.subtract(priorGrossMargin).setScale(6, RoundingMode.HALF_UP), "pct"));
        }

        BigDecimal profit = first(now, "NET_PROFIT_PARENT", "NET_PROFIT");
        BigDecimal priorProfit = first(prior, "NET_PROFIT_PARENT", "NET_PROFIT");
        BigDecimal profitYoy = percentageChange(profit, priorProfit);
        if (profitYoy != null) {
            result.getMetrics().add(metric("NET_PROFIT_PARENT_YOY", "归母净利润同比", profitYoy, "%"));
        }
        BigDecimal netMargin = ratio(profit, revenue);
        if (netMargin != null) {
            result.getMetrics().add(metric("NET_MARGIN", "净利率", netMargin, "%"));
        }
        BigDecimal priorNetMargin = ratio(priorProfit, priorRevenue);
        if (netMargin != null && priorNetMargin != null) {
            result.getMetrics().add(metric("NET_MARGIN_YOY_CHANGE", "净利率同比变化",
                    netMargin.subtract(priorNetMargin).setScale(6, RoundingMode.HALF_UP), "pct"));
        }

        BigDecimal periodExpenses = sum(now, "SELLING_EXPENSE", "ADMIN_EXPENSE",
                "RND_EXPENSE", "FINANCE_EXPENSE");
        BigDecimal periodExpenseRatio = ratio(periodExpenses, revenue);
        if (periodExpenseRatio != null) {
            result.getMetrics().add(metric("PERIOD_EXPENSE_RATIO", "期间费用率",
                    periodExpenseRatio, "%"));
        }

        BigDecimal operatingCash = now.get("OPERATING_CASH_FLOW");
        BigDecimal operatingCashYoy = percentageChange(operatingCash, prior.get("OPERATING_CASH_FLOW"));
        if (operatingCashYoy != null) {
            result.getMetrics().add(metric("OPERATING_CASH_FLOW_YOY", "经营现金流同比",
                    operatingCashYoy, "%"));
        }
        BigDecimal cashToProfit = ratio(operatingCash, profit);
        if (cashToProfit != null) {
            result.getMetrics().add(metric(
                    "OPERATING_CASH_TO_NET_PROFIT", "经营现金流/净利润",
                    cashToProfit, "%"));
            if (cashToProfit.compareTo(new BigDecimal("50")) < 0) {
                result.getFindings().add(finding(
                        "PROFIT_CASH_DIVERGENCE", "HIGH", "RISK",
                        "利润与经营现金流背离",
                        "经营现金流不足净利润的 50%，需要继续核对应收、存货和回款节奏。",
                        "OPERATING_CASH_TO_NET_PROFIT"));
            }
        }

        BigDecimal assets = now.get("TOTAL_ASSETS");
        BigDecimal liabilities = now.get("TOTAL_LIABILITIES");
        BigDecimal debtRatio = ratio(liabilities, assets);
        if (debtRatio != null) {
            result.getMetrics().add(metric("DEBT_TO_ASSETS", "资产负债率", debtRatio, "%"));
        }

        BigDecimal currentAssets = now.get("TOTAL_CURRENT_ASSETS");
        BigDecimal currentLiabilities = first(now,
                "TOTAL_CURRENT_LIABILITIES", "TOTAL_CURRENT_LIAB");
        BigDecimal currentRatio = ratio(currentAssets, currentLiabilities);
        if (currentRatio != null) {
            result.getMetrics().add(metric("CURRENT_RATIO", "流动比率", currentRatio, "%"));
        }
        BigDecimal quickRatio = ratio(subtract(currentAssets, now.get("INVENTORY")), currentLiabilities);
        if (quickRatio != null) {
            result.getMetrics().add(metric("QUICK_RATIO", "速动比率", quickRatio, "%"));
        }

        BigDecimal interestBearingDebt = sum(now, "SHORT_TERM_BORROWINGS",
                "CURRENT_PORTION_LONG_DEBT", "LONG_TERM_BORROWINGS", "BONDS_PAYABLE");
        if (interestBearingDebt != null) {
            result.getMetrics().add(metric("INTEREST_BEARING_DEBT", "有息负债",
                    interestBearingDebt, currency));
        }

        BigDecimal capitalExpenditure = now.get("CAPITAL_EXPENDITURE");
        if (capitalExpenditure != null) {
            result.getMetrics().add(metric("CAPITAL_EXPENDITURE", "资本开支",
                    capitalExpenditure, currency));
            if (operatingCash != null) {
                result.getMetrics().add(metric("FREE_CASH_FLOW", "自由现金流",
                        operatingCash.subtract(capitalExpenditure), currency));
            }
        } else if (operatingCash != null) {
            result.getDataGaps().add("缺少资本开支，无法计算自由现金流");
        }

        BigDecimal contractLiabilitiesYoy = percentageChange(
                first(now, "CONTRACT_LIABILITIES", "CONTRACT_LIAB"),
                first(prior, "CONTRACT_LIABILITIES", "CONTRACT_LIAB"));
        if (contractLiabilitiesYoy != null) {
            result.getMetrics().add(metric("CONTRACT_LIABILITIES_YOY", "合同负债同比",
                    contractLiabilitiesYoy, "%"));
        }

        BigDecimal balanceGap = subtract(assets, sum(now, "TOTAL_LIABILITIES", "TOTAL_EQUITY"));
        if (balanceGap != null) {
            result.getMetrics().add(metric("BALANCE_SHEET_IDENTITY_GAP", "资产负债表恒等式差额",
                    balanceGap, currency));
        }

        BigDecimal receivableYoy = percentageChange(
                now.get("ACCOUNTS_RECEIVABLE"), prior.get("ACCOUNTS_RECEIVABLE"));
        if (receivableYoy != null && revenueYoy != null) {
            BigDecimal spread = receivableYoy.subtract(revenueYoy).setScale(6, RoundingMode.HALF_UP);
            result.getMetrics().add(metric(
                    "RECEIVABLE_GROWTH_MINUS_REVENUE_GROWTH",
                    "应收增速减营收增速", spread, "pct"));
            if (spread.compareTo(new BigDecimal("15")) >= 0) {
                result.getFindings().add(finding(
                        "RECEIVABLES_OUTPACE_REVENUE", "MID", "RISK",
                        "应收账款增长快于收入",
                        "应收账款增速比营业收入增速高 " + spread.stripTrailingZeros().toPlainString()
                                + " 个百分点，需要关注收入确认和回款质量。",
                        "RECEIVABLE_GROWTH_MINUS_REVENUE_GROWTH"));
            }
        }

        BigDecimal inventoryYoy = percentageChange(
                now.get("INVENTORY"), prior.get("INVENTORY"));
        if (inventoryYoy != null && revenueYoy != null) {
            BigDecimal spread = inventoryYoy.subtract(revenueYoy).setScale(6, RoundingMode.HALF_UP);
            result.getMetrics().add(metric(
                    "INVENTORY_GROWTH_MINUS_REVENUE_GROWTH",
                    "存货增速减营收增速", spread, "pct"));
            if (spread.compareTo(new BigDecimal("15")) >= 0) {
                result.getFindings().add(finding(
                        "INVENTORY_OUTPACE_REVENUE", "MID", "RISK",
                        "存货增长快于收入",
                        "存货增速明显快于营业收入，需要结合产品周期、备货和减值政策判断。",
                        "INVENTORY_GROWTH_MINUS_REVENUE_GROWTH"));
            }
        }
        return result;
    }

    private Map<String, BigDecimal> values(List<FinancialLineItem> items) {
        Map<String, BigDecimal> result = new HashMap<String, BigDecimal>();
        if (items == null) {
            return result;
        }
        for (FinancialLineItem item : items) {
            if (item.getConceptCode() != null && item.getNormalizedValue() != null
                    && !result.containsKey(item.getConceptCode())) {
                result.put(item.getConceptCode(), item.getNormalizedValue());
            }
        }
        return result;
    }

    private String currency(List<FinancialLineItem> items) {
        if (items != null) {
            for (FinancialLineItem item : items) {
                if (item.getCurrency() != null && !item.getCurrency().trim().isEmpty()) {
                    return item.getCurrency();
                }
            }
        }
        return "CNY";
    }

    private FinancialMetric metric(String code, String label, BigDecimal value, String unit) {
        FinancialMetric metric = new FinancialMetric();
        metric.setMetricCode(code);
        metric.setLabel(label);
        metric.setValue(value);
        metric.setUnit(unit);
        metric.setFormulaVersion(FORMULA_VERSION);
        metric.setInputRefs(code);
        metric.setQualityStatus(FinancialQualityStatus.FRESH);
        return metric;
    }

    private FinancialFinding finding(String code, String severity, String direction,
                                     String title, String explanation, String refs) {
        FinancialFinding finding = new FinancialFinding();
        finding.setRuleCode(code);
        finding.setRuleVersion(RULE_VERSION);
        finding.setSeverity(severity);
        finding.setDirection(direction);
        finding.setTitle(title);
        finding.setExplanation(explanation);
        finding.setMetricRefs(refs);
        finding.setLimitations("规则只描述可复算现象，不构成买卖建议。");
        return finding;
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal prior) {
        if (current == null || prior == null || prior.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(prior)
                .divide(prior.abs(), 12, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 12, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? null : left.subtract(right);
    }

    private BigDecimal first(Map<String, BigDecimal> values, String... codes) {
        for (String code : codes) {
            if (values.get(code) != null) {
                return values.get(code);
            }
        }
        return null;
    }

    private BigDecimal sum(Map<String, BigDecimal> values, String... codes) {
        BigDecimal result = BigDecimal.ZERO;
        boolean found = false;
        for (String code : codes) {
            BigDecimal value = values.get(code);
            if (value != null) {
                result = result.add(value);
                found = true;
            }
        }
        return found ? result : null;
    }
}
