package com.finscope.service.factorresearch;

import com.finscope.domain.quant.factor.FactorAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies interpretation boundaries to deterministic IC statistics. This is a
 * diagnostic gate, not a lifecycle validation policy.
 */
public class FactorEvidenceAssessmentService {
    private static final int MIN_DAILY_IC_SAMPLES = 20;

    public void assess(FactorAnalysis analysis, String direction, String dataKind) {
        boolean negative = "NEGATIVE_HYPOTHESIS".equals(direction);
        double alignedMean = analysis.getIcMean() * (negative ? -1d : 1d);
        double favorableRatio = negative ? 1d - analysis.getPositiveIcRatio() : analysis.getPositiveIcRatio();
        analysis.setEvaluationMode("CROSS_SECTIONAL_FACTOR_STUDY");
        analysis.setResearchDirection(direction);
        analysis.setDirectionAdjustedIcMean(alignedMean);
        analysis.setFavorableIcRatio(favorableRatio);

        if (analysis.getSampleCount() < MIN_DAILY_IC_SAMPLES) {
            analysis.setSampleEvidence("INSUFFICIENT_SAMPLE");
        } else if (alignedMean >= 0.02d && favorableRatio >= 0.55d) {
            analysis.setSampleEvidence("DIRECTIONALLY_ALIGNED");
        } else if (alignedMean <= -0.02d && favorableRatio <= 0.45d) {
            analysis.setSampleEvidence("OPPOSED");
        } else {
            analysis.setSampleEvidence("UNSTABLE");
        }

        List<String> caveats = new ArrayList<String>();
        if ("LEARNING_SAMPLE".equals(dataKind)) {
            caveats.add("虚拟学习数据只能验证流程，不能证明真实市场有效性");
        }
        if (analysis.getSampleCount() < MIN_DAILY_IC_SAMPLES) {
            caveats.add("有效日度 IC 少于 20 个，结果容易被少数交易日影响");
        }
        caveats.add("尚未完成样本外、市场阶段、成本压力和多重检验");
        caveats.add("当前 ICIR 未年化，sampleCount 是有效交易日数而不是股票数");
        analysis.setCaveats(caveats);
        analysis.setConclusion("INCONCLUSIVE");
    }
}
