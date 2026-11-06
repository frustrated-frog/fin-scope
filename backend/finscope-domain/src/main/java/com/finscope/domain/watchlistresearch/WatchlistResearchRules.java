package com.finscope.domain.watchlistresearch;

import com.finscope.common.enums.watchlistresearch.ResearchSignalKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 方法版本v1的观察项触发规则，排序幅度不作为投资评分。 */
public final class WatchlistResearchRules {
    private WatchlistResearchRules() {
    }

    public static List<ResearchFinding> findings(WatchlistResearchSnapshot snapshot) {
        List<ResearchFinding> findings = new ArrayList<ResearchFinding>();
        for (StockStrengthAnalysis stock : snapshot.getStrengths()) {
            if (stock.getExcessReturnPp() == null || !stock.getGaps().isEmpty()) {
                continue;
            }
            boolean strong = stock.getExcessReturnPp() >= 5 && stock.getPositiveResidualDays() >= 13;
            boolean weak = stock.getExcessReturnPp() <= -5 && stock.getPositiveResidualDays() <= 7;
            if (strong || weak) {
                findings.add(finding(strong ? ResearchSignalKind.OUTPERFORMING : ResearchSignalKind.UNDERPERFORMING,
                        List.of(stock.getCode()), stock.getName() + (strong ? " 持续强于市场基线" : " 持续弱于市场基线"),
                        Math.abs(stock.getExcessReturnPp()), List.of(
                                format("近20日实际 %+.2f%%，统计基线 %+.2f%%，偏离 %+.2f 个百分点",
                                        stock.getActualReturnPct(), stock.getBaselineReturnPct(), stock.getExcessReturnPp()),
                                format("20日中有%d日高于基线；beta %.2f，训练样本%d日，拟合度 %.0f%%",
                                        stock.getPositiveResidualDays(), stock.getBeta(), stock.getTrainingSamples(),
                                        stock.getFitQuality() * 100),
                                stock.getEvaluatedFrom() + " → " + stock.getEvaluatedThrough())));
            }
        }
        for (StockRelationship pair : snapshot.getRelationships()) {
            if (pair.isStrong()) {
                findings.add(finding(ResearchSignalKind.RESIDUAL_LINK,
                        List.of(pair.getLeftCode(), pair.getRightCode()),
                        pair.getLeftName() + " / " + pair.getRightName() + " 存在紧密联动",
                        pair.getResidualCorrelation() * 10,
                        List.of(format("原始相关 %.2f，扣除沪深300后的残差相关 %.2f，共%d个日收益样本",
                                pair.getCorrelation(), pair.getResidualCorrelation(), pair.getSamples()),
                                pair.getFrom() + " → " + pair.getThrough())));
            }
        }
        for (StockGrowthQuality stock : snapshot.getGrowthQuality()) {
            addFinancialFindings(findings, stock);
        }
        findings.sort(Comparator.comparingDouble(ResearchFinding::getMagnitude).reversed()
                .thenComparing(ResearchFinding::getKey));
        return findings;
    }

    private static void addFinancialFindings(List<ResearchFinding> findings, StockGrowthQuality stock) {
        if (stock.getCashConversion() != null && stock.getCashConversion() < 0) {
            findings.add(finding(ResearchSignalKind.NEGATIVE_CASH_FLOW, List.of(stock.getCode()),
                    stock.getName() + " 盈利但经营现金流为负", 10 + Math.min(20, -stock.getCashConversion() * 10),
                    List.of(format("滚动四季现金转化 %.2f 倍，归母利润 %s，经营现金流 %s %s",
                                    stock.getCashConversion(), stock.getProfitTtm().toPlainString(),
                                    stock.getOperatingCashFlowTtm().toPlainString(), stock.getCurrency()),
                            stock.getPeriodFrom() + " → " + stock.getPeriodThrough())));
        } else if (stock.getProfitGrowthPct() != null && stock.getProfitGrowthPct() >= 10
                && stock.getCashConversion() != null && stock.getCashConversion() < 0.5) {
            findings.add(finding(ResearchSignalKind.CASH_LAG, List.of(stock.getCode()),
                    stock.getName() + " 利润增长与现金转化不同步", 10 + (0.5 - stock.getCashConversion()) * 10,
                    List.of(format("滚动四季利润增长 %.2f%%，经营现金流 / 归母利润仅 %.2f 倍",
                                    stock.getProfitGrowthPct(), stock.getCashConversion()),
                            stock.getPeriodFrom() + " → " + stock.getPeriodThrough())));
        }
        if (stock.getRevenueGrowthPct() != null && stock.getRevenueGrowthPct() >= 10
                && stock.getGrossMarginChangePp() != null && stock.getGrossMarginChangePp() <= -2) {
            findings.add(finding(ResearchSignalKind.MARGIN_EROSION, List.of(stock.getCode()),
                    stock.getName() + " 收入增长但毛利率回落", Math.abs(stock.getGrossMarginChangePp()) + 5,
                    List.of(format("滚动四季收入增长 %.2f%%，收入加权毛利率 %.2f%%，同比变化 %+.2f 个百分点",
                                    stock.getRevenueGrowthPct(), stock.getGrossMarginPct(), stock.getGrossMarginChangePp()),
                            stock.getPeriodFrom() + " → " + stock.getPeriodThrough())));
        }
    }

    private static ResearchFinding finding(ResearchSignalKind kind, List<String> codes,
                                            String title, double magnitude, List<String> evidence) {
        ResearchFinding result = new ResearchFinding();
        result.setKind(kind);
        result.setCodes(codes);
        result.setKey(kind.name() + ":" + String.join(",", codes));
        result.setTitle(title);
        result.setMagnitude(magnitude);
        result.setEvidence(evidence);
        return result;
    }

    private static String format(String template, Object... values) {
        return String.format(Locale.ROOT, template, values);
    }
}
