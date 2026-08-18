package com.finscope.service.quant.academy;

import com.finscope.domain.quant.academy.QuantStrategyAcademyCard;
import com.finscope.domain.quant.backtest.AnnualPerformance;
import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.experiment.QuantExperiment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class QuantStrategyEvidenceScorer {
    private static final int APPLICATION_SCORE = 70;
    private static final int OBSERVATION_SCORE = 55;

    public QuantStrategyAcademyCard score(QuantStrategyCandidate candidate, QuantDataset dataset,
                                             QuantExperiment experiment) {
        QuantStrategyAcademyCard card = baseCard(candidate, dataset, experiment);
        if (dataset != null && !"REAL".equals(dataset.getDataKind())) {
            learningCase(card, "虚拟学习数据不能形成真实历史证据");
            card.getLimitations().add("当前结果来自虚拟学习数据，只能用于理解流程");
            return card;
        }
        if (experiment == null) {
            replication(card, "等待生成本地策略版本并运行历史实验", "LEARNING");
            return card;
        }
        if ("QUEUED".equals(experiment.getStatus()) || "RUNNING".equals(experiment.getStatus())) {
            replication(card, "正在使用冻结数据与确定性引擎运行本地实验", "VALIDATING");
            return card;
        }
        if (!"SUCCEEDED".equals(experiment.getStatus()) || experiment.getResult() == null) {
            learningCase(card, "本地实验未完成，保留为失败学习案例");
            if (text(experiment.getErrorMessage())) {
                card.getLimitations().add(experiment.getErrorMessage());
            }
            return card;
        }
        historical(card, experiment.getResult());
        return card;
    }

    private QuantStrategyAcademyCard baseCard(QuantStrategyCandidate candidate, QuantDataset dataset,
                                                  QuantExperiment experiment) {
        QuantStrategyAcademyCard card = new QuantStrategyAcademyCard();
        card.setCandidateId(candidate.getId());
        card.setTitle(candidate.getTitle());
        card.setPaperUrl(candidate.getPaperUrl());
        card.setImplementationUrl(candidate.getImplementationUrl());
        card.setAdaptationNote(candidate.getAdaptationNote());
        card.setMappedFactors(new ArrayList<String>(candidate.getMappedFactors()));
        if (dataset != null) {
            card.setDatasetId(dataset.getId());
            card.setDatasetName(dataset.getName());
        }
        if (experiment != null) {
            card.setExperimentId(experiment.getId());
            card.setExperimentStatus(experiment.getStatus());
        }
        learningCopy(card);
        return card;
    }

    private void historical(QuantStrategyAcademyCard card, BacktestResult result) {
        BacktestMetrics metrics = result.getMetrics();
        List<AnnualPerformance> years = result.getAnnualPerformance() == null
                ? Collections.<AnnualPerformance>emptyList() : result.getAnnualPerformance();
        List<String> warnings = result.getWarnings() == null
                ? Collections.<String>emptyList() : result.getWarnings();
        int positiveExcessYears = 0;
        for (AnnualPerformance year : years) {
            if (year.getExcessReturn() > 0) {
                positiveExcessYears++;
            }
            QuantStrategyAcademyCard.YearEvidence evidence = new QuantStrategyAcademyCard.YearEvidence();
            evidence.setYear(year.getYear());
            evidence.setPortfolioReturn(year.getPortfolioReturn());
            evidence.setBenchmarkReturn(year.getBenchmarkReturn());
            evidence.setExcessReturn(year.getExcessReturn());
            evidence.setMaxDrawdown(year.getMaxDrawdown());
            card.getAnnualEvidence().add(evidence);
        }
        double positiveRatio = years.isEmpty() ? 0 : (double) positiveExcessYears / years.size();
        QuantStrategyAcademyCard.Metrics cardMetrics = new QuantStrategyAcademyCard.Metrics();
        cardMetrics.setAnnualizedReturn(metrics.getAnnualizedReturn());
        cardMetrics.setExcessReturn(metrics.getExcessReturn());
        cardMetrics.setMaxDrawdown(metrics.getMaxDrawdown());
        cardMetrics.setSharpeRatio(metrics.getSharpeRatio());
        cardMetrics.setCalmarRatio(metrics.getCalmarRatio());
        cardMetrics.setTurnover(metrics.getTurnover());
        cardMetrics.setTradeCount(metrics.getTradeCount());
        cardMetrics.setYearCount(years.size());
        cardMetrics.setPositiveExcessYearRatio(positiveRatio);
        card.setMetrics(cardMetrics);

        addCoverage(card, years.size(), metrics.getTradeCount());
        addBenchmark(card, metrics);
        addRisk(card, metrics);
        addStability(card, positiveRatio);
        addExecution(card, metrics.getTurnover(), warnings);
        int score = card.getDimensions().stream().mapToInt(QuantStrategyAcademyCard.ScoreDimension::getScore).sum();
        card.setEvidenceScore(score);
        card.getLimitations().addAll(warnings);
        card.getLimitations().add("本地历史回测不等同于实盘或未来收益证明");
        if (score >= APPLICATION_SCORE) {
            card.setEvidenceLevel("HISTORICAL_EVIDENCE");
            card.setShelf("APPLICATION_CANDIDATE");
            card.setEvidenceSummary("本地历史证据相对完整，可进入影子组合继续观察");
        } else if (score >= OBSERVATION_SCORE) {
            card.setEvidenceLevel("HISTORICAL_EVIDENCE");
            card.setShelf("OBSERVATION");
            card.setEvidenceSummary("已有本地历史证据，但仍需观察稳定性与风险");
        } else {
            learningCase(card, "历史实验已完成，但综合证据偏弱，保留为学习案例");
            card.setEvidenceScore(score);
        }
    }

    private void addCoverage(QuantStrategyAcademyCard card, int yearCount, int tradeCount) {
        int score = 0;
        if (yearCount >= 3) {
            score += 10;
        } else if (yearCount >= 2) {
            score += 5;
        }
        if (tradeCount >= 20) {
            score += 10;
        } else if (tradeCount >= 10) {
            score += 5;
        }
        card.getDimensions().add(dimension("COVERAGE", "样本覆盖", score, 20,
                yearCount + " 个年度 · " + tradeCount + " 笔成交"));
    }

    private void addBenchmark(QuantStrategyAcademyCard card, BacktestMetrics metrics) {
        int score = 0;
        if (metrics.getAnnualizedReturn() > 0) {
            score += 10;
        }
        if (metrics.getExcessReturn() > 0) {
            score += 15;
        }
        card.getDimensions().add(dimension("BENCHMARK", "基准比较", score, 25,
                "年化收益与等权基准进行同口径比较"));
    }

    private void addRisk(QuantStrategyAcademyCard card, BacktestMetrics metrics) {
        int score = 0;
        if (metrics.getMaxDrawdown() <= 0.35) {
            score += 12;
        } else if (metrics.getMaxDrawdown() <= 0.50) {
            score += 6;
        }
        if (metrics.getCalmarRatio() > 0) {
            score += 8;
        }
        card.getDimensions().add(dimension("RISK", "风险控制", score, 20,
                "最大回撤与收益回撤比共同评价"));
    }

    private void addStability(QuantStrategyAcademyCard card, double positiveRatio) {
        int score = 0;
        if (positiveRatio >= 0.60) {
            score = 20;
        } else if (positiveRatio >= 0.40) {
            score = 12;
        } else if (positiveRatio > 0) {
            score = 6;
        }
        card.getDimensions().add(dimension("STABILITY", "年度稳定性", score, 20,
                String.format("%.0f%% 年度取得正超额", positiveRatio * 100)));
    }

    private void addExecution(QuantStrategyAcademyCard card, double turnover, List<String> warnings) {
        int score = 0;
        if (turnover <= 4) {
            score += 10;
        }
        if (warnings.isEmpty()) {
            score += 5;
        }
        card.getDimensions().add(dimension("EXECUTION", "可执行性", score, 15,
                warnings.isEmpty() ? "换手与执行未出现额外警告" : "实验包含执行或数据警告"));
    }

    private QuantStrategyAcademyCard.ScoreDimension dimension(String code, String label, int score,
                                                                  int maxScore, String explanation) {
        return new QuantStrategyAcademyCard.ScoreDimension(code, label, score, maxScore, explanation);
    }

    private void replication(QuantStrategyAcademyCard card, String summary, String shelf) {
        card.setEvidenceLevel("RESEARCH_REPLICATION");
        card.setShelf(shelf);
        card.setEvidenceScore(0);
        card.setEvidenceSummary(summary);
        card.getLimitations().add("公开来源与本地适配不代表该策略已经通过本地验证");
    }

    private void learningCase(QuantStrategyAcademyCard card, String summary) {
        card.setEvidenceLevel("LEARNING_CASE");
        card.setShelf("LEARNING_CASE");
        card.setEvidenceSummary(summary);
    }

    private void learningCopy(QuantStrategyAcademyCard card) {
        String factors = String.join("、", card.getMappedFactors());
        card.setEarningLogic(factors.isEmpty() ? "研究价格与基本面特征形成的长期差异"
                : "利用“" + factors + "”在股票之间形成的相对差异");
        card.setRationale("规则只使用当时可获得的数据，对候选股票排序并低频等权持有");
        card.setSuitableRegime("更适合流动性正常、横截面差异能够持续体现的市场阶段");
        card.setInvalidationRisk("因子拥挤、市场风格切换、交易成本上升或数据覆盖不足都可能使策略失效");
    }

    private boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
