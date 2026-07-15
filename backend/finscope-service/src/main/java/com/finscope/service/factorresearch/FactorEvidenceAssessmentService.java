package com.finscope.service.factorresearch;

import com.finscope.domain.quant.factor.FactorAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a versioned, deterministic evidence gate to daily cross-sectional IC.
 */
public class FactorEvidenceAssessmentService {
    public void assess(FactorAnalysis analysis, String direction, String dataKind) {
        assess(analysis, direction, dataKind, "REAL".equals(dataKind) ? "RESEARCH" : "LEARNING");
    }

    public void assess(FactorAnalysis analysis, String direction, String dataKind, String datasetLevel) {
        boolean negative = "NEGATIVE_HYPOTHESIS".equals(direction);
        double alignedMean = analysis.getIcMean() * (negative ? -1d : 1d);
        double favorableRatio = negative ? analysis.getNegativeIcRatio() : analysis.getPositiveIcRatio();
        double alignedLower = negative ? -analysis.getIcMeanCiUpper() : analysis.getIcMeanCiLower();
        double alignedUpper = negative ? -analysis.getIcMeanCiLower() : analysis.getIcMeanCiUpper();
        analysis.setEvaluationMode("CROSS_SECTIONAL_FACTOR_STUDY");
        analysis.setEvaluationPolicyVersion(FactorValidationPolicy.VERSION);
        analysis.setResearchDirection(direction);
        analysis.setDirectionAdjustedIcMean(alignedMean);
        analysis.setFavorableIcRatio(favorableRatio);
        analysis.setDirectionAdjustedCiLower(alignedLower);
        analysis.setDirectionAdjustedCiUpper(alignedUpper);
        analysis.setDirectionAdjustedQuantileSpread(analysis.getQuantileSpreadMean() * (negative ? -1d : 1d));
        analysis.setDirectionAdjustedMonotonicity(analysis.getQuantileMonotonicityMean() * (negative ? -1d : 1d));

        if (analysis.getSampleCount() < FactorValidationPolicy.MIN_VALID_IC_DAYS) {
            analysis.setSampleEvidence("INSUFFICIENT_SAMPLE");
        } else if (alignedMean >= FactorValidationPolicy.MIN_DIRECTION_ADJUSTED_IC
                && favorableRatio >= FactorValidationPolicy.MIN_FAVORABLE_RATIO && alignedLower > 0d) {
            analysis.setSampleEvidence("DIRECTIONALLY_ALIGNED");
        } else if (alignedMean <= -FactorValidationPolicy.MIN_DIRECTION_ADJUSTED_IC
                && favorableRatio <= 1d - FactorValidationPolicy.MIN_FAVORABLE_RATIO && alignedUpper < 0d) {
            analysis.setSampleEvidence("OPPOSED");
        } else {
            analysis.setSampleEvidence("UNSTABLE");
        }

        List<String> caveats = new ArrayList<String>();
        List<String> blocking = new ArrayList<String>();
        if ("LEARNING_SAMPLE".equals(dataKind)) {
            caveats.add("虚拟学习数据只能验证流程，不能证明真实市场有效性");
            blocking.add("LEARNING_DATA_NOT_VALIDATION_EVIDENCE");
        }
        if (!"RESEARCH".equals(datasetLevel)) blocking.add("DATASET_LEVEL_NOT_RESEARCH");
        if (analysis.getSampleCount() < FactorValidationPolicy.MIN_VALID_IC_DAYS) {
            caveats.add("有效日度 IC 少于 60 个，结果容易被少数交易日影响");
            blocking.add("VALID_IC_DAYS_BELOW_60");
        }
        if (analysis.getMinCrossSectionSize() < FactorValidationPolicy.MIN_CROSS_SECTION_SIZE) {
            caveats.add("有效日期的最小横截面少于 10 只标的，秩相关不稳定");
            blocking.add("CROSS_SECTION_BELOW_10");
        }
        if (analysis.getQuantileSampleDays() < FactorValidationPolicy.MIN_VALID_IC_DAYS) {
            caveats.add("有效分位组检验少于 60 个交易日，组合证据不足");
            blocking.add("QUANTILE_DAYS_BELOW_60");
        }
        if (analysis.getCoverageRatio() < FactorValidationPolicy.MIN_COVERAGE_RATIO) {
            caveats.add("有效日期覆盖率低于 80%，结果可能集中在少数可计算日期");
            blocking.add("VALID_DATE_COVERAGE_BELOW_80_PERCENT");
        }
        caveats.add("尚未完成样本外、市场阶段、成本压力和多重检验");
        caveats.add("当前 ICIR 未年化，sampleCount 是有效交易日数而不是股票数");
        analysis.setCaveats(caveats);
        boolean eligible = blocking.isEmpty();
        analysis.setValidationEligible(eligible);
        analysis.setBlockingReasons(blocking);
        if (eligible && "DIRECTIONALLY_ALIGNED".equals(analysis.getSampleEvidence())
                && analysis.getDirectionAdjustedQuantileSpread() > 0d
                && analysis.getDirectionAdjustedMonotonicity() > 0d) {
            analysis.setConclusion("SUPPORTED");
        } else if (eligible && "OPPOSED".equals(analysis.getSampleEvidence())
                && analysis.getDirectionAdjustedQuantileSpread() < 0d
                && analysis.getDirectionAdjustedMonotonicity() < 0d) {
            analysis.setConclusion("REFUTED");
        } else {
            analysis.setConclusion("INCONCLUSIVE");
        }
    }
}
