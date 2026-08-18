package com.finscope.service.quant.academy;

import com.finscope.common.enums.quant.QuantStrategyAcademyShelf;
import com.finscope.common.enums.quant.QuantStrategyEvidenceLevel;
import com.finscope.common.enums.quant.QuantStrategyDraftStatus;
import com.finscope.domain.quant.academy.QuantStrategyAcademyCard;
import com.finscope.domain.quant.backtest.AnnualPerformance;
import com.finscope.domain.quant.backtest.BacktestMetrics;
import com.finscope.domain.quant.backtest.BacktestResult;
import com.finscope.domain.quant.backtest.EquityPoint;
import com.finscope.domain.quant.catalog.QuantStrategyCandidate;
import com.finscope.domain.quant.data.QuantDataset;
import com.finscope.domain.quant.experiment.QuantExperiment;
import com.finscope.domain.quant.strategy.QuantStrategyDraft;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantStrategyEvidenceScorerTest {

    private final QuantStrategyEvidenceScorer scorer = new QuantStrategyEvidenceScorer();

    @Test
    void gradesStrongRealHistoryAsApplicationCandidate() {
        QuantExperiment experiment = succeeded(0.18, 0.09, 0.19, 1.15, 0.94, 48, 2.3,
                years(0.05, 0.03, 0.08, 0.02), Collections.<String>emptyList());

        QuantStrategyAcademyCard card = scorer.score(candidate(), realDataset(), experiment);

        assertEquals(QuantStrategyEvidenceLevel.HISTORICAL_EVIDENCE, card.getEvidenceLevel());
        assertEquals(QuantStrategyAcademyShelf.APPLICATION_CANDIDATE, card.getShelf());
        assertEquals(100, card.getEvidenceScore());
        assertEquals(5, card.getDimensions().size());
        assertTrue(card.getLimitations().stream().anyMatch(value -> value.contains("不等同于实盘")));
    }

    @Test
    void keepsModerateHistoryVisibleOnObservationShelf() {
        QuantExperiment experiment = succeeded(0.06, -0.01, 0.31, 0.42, 0.38, 27, 8.0,
                years(0.03, -0.02), Collections.singletonList("部分交易日股票池覆盖不足"));

        QuantStrategyAcademyCard card = scorer.score(candidate(), realDataset(), experiment);

        assertEquals(QuantStrategyEvidenceLevel.HISTORICAL_EVIDENCE, card.getEvidenceLevel());
        assertEquals(QuantStrategyAcademyShelf.OBSERVATION, card.getShelf());
        assertEquals(57, card.getEvidenceScore());
        assertTrue(card.getLimitations().contains("部分交易日股票池覆盖不足"));
    }

    @Test
    void turnsWeakSuccessfulExperimentIntoLearningCaseInsteadOfHidingIt() {
        QuantExperiment experiment = succeeded(-0.08, -0.12, 0.52, -0.18, 0.27, 6, 12.0,
                years(-0.08), Collections.singletonList("样本不足"));

        QuantStrategyAcademyCard card = scorer.score(candidate(), realDataset(), experiment);

        assertEquals(QuantStrategyEvidenceLevel.LEARNING_CASE, card.getEvidenceLevel());
        assertEquals(QuantStrategyAcademyShelf.LEARNING_CASE, card.getShelf());
        assertTrue(card.getEvidenceScore() < 55);
        assertTrue(card.getEvidenceSummary().contains("学习案例"));
    }

    @Test
    void doesNotCallRunningOrSyntheticExperimentsHistoricalEvidence() {
        QuantExperiment running = new QuantExperiment();
        running.setStatus("RUNNING");

        QuantStrategyAcademyCard pending = scorer.score(candidate(), realDataset(), running);
        QuantDataset sample = realDataset();
        sample.setDataKind("LEARNING_SAMPLE");
        QuantStrategyAcademyCard synthetic = scorer.score(candidate(), sample,
                succeeded(0.20, 0.10, 0.12, 1.3, 1.2, 60, 2.0,
                        years(0.06, 0.07, 0.04), Collections.<String>emptyList()));

        assertEquals(QuantStrategyEvidenceLevel.RESEARCH_REPLICATION, pending.getEvidenceLevel());
        assertEquals(QuantStrategyAcademyShelf.VALIDATING, pending.getShelf());
        assertEquals(QuantStrategyEvidenceLevel.LEARNING_CASE, synthetic.getEvidenceLevel());
        assertEquals(QuantStrategyAcademyShelf.LEARNING_CASE, synthetic.getShelf());
        assertTrue(synthetic.getLimitations().stream().anyMatch(value -> value.contains("虚拟学习数据")));
    }

    @Test
    void preservesFailedExperimentAsLearningEvidence() {
        QuantExperiment failed = new QuantExperiment();
        failed.setStatus("FAILED");
        failed.setErrorMessage("回测区间内无可成交标的");

        QuantStrategyAcademyCard card = scorer.score(candidate(), realDataset(), failed);

        assertEquals(QuantStrategyEvidenceLevel.LEARNING_CASE, card.getEvidenceLevel());
        assertEquals(QuantStrategyAcademyShelf.LEARNING_CASE, card.getShelf());
        assertTrue(card.getLimitations().contains("回测区间内无可成交标的"));
    }

    @Test
    void preservesFailedDraftValidationIssuesAsLearningEvidence() {
        QuantStrategyDraft draft = new QuantStrategyDraft();
        draft.setStatus(QuantStrategyDraftStatus.FAILED);
        draft.setValidationIssues(Collections.singletonList("换手周期不受支持"));

        QuantStrategyAcademyCard card = scorer.failedDraft(candidate(), realDataset(), draft);

        assertEquals(QuantStrategyEvidenceLevel.LEARNING_CASE, card.getEvidenceLevel());
        assertEquals(QuantStrategyAcademyShelf.LEARNING_CASE, card.getShelf());
        assertTrue(card.getLimitations().contains("换手周期不受支持"));
    }

    @Test
    void distinguishesBuildInterruptionFromProtocolFailure() {
        QuantStrategyDraft draft = new QuantStrategyDraft();
        draft.setStatus(QuantStrategyDraftStatus.BUILD_FAILED);
        draft.setValidationIssues(Collections.singletonList("数据库暂时不可用"));

        QuantStrategyAcademyCard card = scorer.failedDraft(candidate(), realDataset(), draft);

        assertTrue(card.getEvidenceSummary().contains("不代表策略协议失败"));
        assertTrue(card.getLimitations().contains("数据库暂时不可用"));
    }

    @Test
    void doesNotPromoteAHighScoreWithoutThreeCalendarYears() {
        QuantExperiment experiment = succeeded(0.18, 0.09, 0.19, 1.15, 0.94, 48, 2.3,
                Collections.<AnnualPerformance>emptyList(), Collections.<String>emptyList());

        QuantStrategyAcademyCard card = scorer.score(candidate(), realDataset(), experiment);

        assertEquals(QuantStrategyAcademyShelf.OBSERVATION, card.getShelf());
        assertTrue(card.getLimitations().stream().anyMatch(value -> value.contains("实际覆盖至少 3 年")));
    }

    @Test
    void doesNotTreatThreePartialCalendarYearsAsThreeYearsOfEvidence() {
        QuantExperiment experiment = succeeded(0.18, 0.09, 0.19, 1.15, 0.94, 48, 2.3,
                years(0.05, 0.03, 0.08), Collections.<String>emptyList());
        experiment.getResult().setEquityCurve(curve(LocalDate.of(2024, 12, 1), LocalDate.of(2026, 1, 31)));

        QuantStrategyAcademyCard card = scorer.score(candidate(), realDataset(), experiment);

        assertEquals(QuantStrategyAcademyShelf.OBSERVATION, card.getShelf());
        assertTrue(card.getLimitations().stream().anyMatch(value -> value.contains("不能只跨过 3 个自然年份")));
    }

    @Test
    void doesNotPromoteAHighScoreWhenExecutionWarningsRemain() {
        QuantExperiment experiment = succeeded(0.18, 0.09, 0.19, 1.15, 0.94, 48, 2.3,
                years(0.05, 0.03, 0.08, 0.02), Collections.singletonList("部分交易日股票池覆盖不足"));

        QuantStrategyAcademyCard card = scorer.score(candidate(), realDataset(), experiment);

        assertEquals(QuantStrategyAcademyShelf.OBSERVATION, card.getShelf());
        assertTrue(card.getLimitations().stream().anyMatch(value -> value.contains("实验警告")));
    }

    private QuantStrategyCandidate candidate() {
        QuantStrategyCandidate value = new QuantStrategyCandidate();
        value.setId(7L);
        value.setTitle("质量价值策略");
        value.setPaperUrl("https://example.com/paper");
        value.setImplementationUrl("https://example.com/code");
        value.setCompatibilityStatus("ADAPTABLE");
        value.setAdaptationNote("使用披露时点质量与价值因子形成 A 股多头版本");
        value.setMappedFactors(Arrays.asList("ROE", "BP"));
        return value;
    }

    private QuantDataset realDataset() {
        QuantDataset value = new QuantDataset();
        value.setId(3L);
        value.setName("A股真实研究集");
        value.setDataKind("REAL");
        value.setStatus("READY");
        value.setFingerprint("dataset-fingerprint");
        return value;
    }

    private QuantExperiment succeeded(double annualReturn, double excessReturn, double drawdown,
                                          double sharpe, double calmar, int trades, double turnover,
                                          java.util.List<AnnualPerformance> years,
                                          java.util.List<String> warnings) {
        BacktestMetrics metrics = new BacktestMetrics();
        metrics.setAnnualizedReturn(annualReturn);
        metrics.setExcessReturn(excessReturn);
        metrics.setMaxDrawdown(drawdown);
        metrics.setSharpeRatio(sharpe);
        metrics.setCalmarRatio(calmar);
        metrics.setTradeCount(trades);
        metrics.setTurnover(turnover);
        BacktestResult result = new BacktestResult();
        result.setMetrics(metrics);
        result.setAnnualPerformance(years);
        result.setWarnings(warnings);
        if (!years.isEmpty()) {
            result.setEquityCurve(curve(LocalDate.of(years.get(0).getYear(), 1, 1),
                    LocalDate.of(years.get(years.size() - 1).getYear(), 12, 31)));
        }
        QuantExperiment value = new QuantExperiment();
        value.setId(19L);
        value.setStatus("SUCCEEDED");
        value.setDataKind("REAL");
        value.setResult(result);
        return value;
    }

    private java.util.List<EquityPoint> curve(LocalDate start, LocalDate end) {
        EquityPoint first = new EquityPoint();
        first.setTradeDate(start);
        EquityPoint last = new EquityPoint();
        last.setTradeDate(end);
        return Arrays.asList(first, last);
    }

    private java.util.List<AnnualPerformance> years(double... excessReturns) {
        java.util.List<AnnualPerformance> values = new java.util.ArrayList<AnnualPerformance>();
        for (int index = 0; index < excessReturns.length; index++) {
            AnnualPerformance value = new AnnualPerformance();
            value.setYear(2020 + index);
            value.setPortfolioReturn(excessReturns[index] + 0.05);
            value.setBenchmarkReturn(0.05);
            value.setExcessReturn(excessReturns[index]);
            value.setMaxDrawdown(0.18);
            values.add(value);
        }
        return values;
    }
}
