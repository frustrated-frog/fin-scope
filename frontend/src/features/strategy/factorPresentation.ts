import {
  FactorLifecycleStatus,
  FactorResearchDirection,
  QuantDataset,
  QuantFactorAnalysis
} from './quantTypes';

const lifecycleLabels: Record<FactorLifecycleStatus, string> = {
  CANDIDATE: '候选定义',
  DEFINITION_REVIEWED: '定义已评审',
  IMPLEMENTED: '实现完成',
  CALCULATION_VERIFIED: '计算已核验',
  EXPLORATORY: '探索中',
  VALIDATED: '已验证',
  PRODUCTION_ELIGIBLE: '具备生产资格',
  INVALIDATED: '已失效',
  RETIRED: '已退役'
};

export function lifecycleLabel(status: FactorLifecycleStatus) {
  return lifecycleLabels[status];
}

export function researchDirectionLabel(direction: FactorResearchDirection) {
  return direction === 'NEGATIVE_HYPOTHESIS'
    ? '默认研究方向：数值越低越符合假设（尚需验证）'
    : '默认研究方向：数值越高越符合假设（尚需验证）';
}

export function datasetEvidenceNotice(dataKind: QuantDataset['dataKind']) {
  return dataKind === 'LEARNING_SAMPLE'
    ? '这是虚拟学习数据，只能学习研究流程，不能证明真实市场有效性。'
    : '这是冻结真实数据，但单次样本内诊断仍不能等同于因子已验证。';
}

export interface FactorAnalysisExplanation {
  headline: string;
  detail: string;
  directionAdjustedIcMean: number;
  favorableIcRatio: number;
  evidenceLevel: 'INSUFFICIENT' | 'UNSTABLE' | 'DIRECTIONALLY_ALIGNED';
}

export function explainFactorAnalysis(
  analysis: QuantFactorAnalysis,
  direction: FactorResearchDirection,
  dataKind: QuantDataset['dataKind']
): FactorAnalysisExplanation {
  const sign = direction === 'NEGATIVE_HYPOTHESIS' ? -1 : 1;
  const directionAdjustedIcMean = analysis.directionAdjustedIcMean ?? analysis.icMean * sign;
  const favorableIcRatio = analysis.favorableIcRatio ?? (direction === 'NEGATIVE_HYPOTHESIS'
    ? (analysis.negativeIcRatio ?? 1 - analysis.positiveIcRatio)
    : analysis.positiveIcRatio);
  const directionText = direction === 'NEGATIVE_HYPOTHESIS' ? '低值方向' : '高值方向';
  const commonBoundary = `本结果评价同日股票池的横截面排序，不是个股涨跌预测。${datasetEvidenceNotice(dataKind)}`;

  if (analysis.sampleEvidence === 'INSUFFICIENT_SAMPLE' || analysis.sampleCount < 60) {
    return {
      headline: '当前样本不足，暂时不能判断方向',
      detail: `只有 ${analysis.sampleCount} 个有效日度 IC 样本，未达到版本化门禁要求，容易被少数交易日影响。${commonBoundary}`,
      directionAdjustedIcMean,
      favorableIcRatio,
      evidenceLevel: 'INSUFFICIENT'
    };
  }
  if (analysis.sampleEvidence === 'OPPOSED') {
    return {
      headline: '当前证据与预设方向相反',
      detail: `${directionText}在当前样本中被确定性门禁反驳，但仍需在独立样本复核。${commonBoundary}`,
      directionAdjustedIcMean,
      favorableIcRatio,
      evidenceLevel: 'UNSTABLE'
    };
  }
  if (analysis.sampleEvidence === 'UNSTABLE'
      || directionAdjustedIcMean < 0.02 || favorableIcRatio < 0.55) {
    return {
      headline: '当前样本中的方向不稳定',
      detail: `${directionText}与未来收益排序的一致性不足，不能据此称因子有效。${commonBoundary}`,
      directionAdjustedIcMean,
      favorableIcRatio,
      evidenceLevel: 'UNSTABLE'
    };
  }
  return {
    headline: '当前样本与预设方向较一致，仍需稳健性检验',
    detail: `${directionText}在这份数据中的平均排序关系与假设同向，但尚未完成样本外、成本和市场阶段检验。${commonBoundary}`,
    directionAdjustedIcMean,
    favorableIcRatio,
    evidenceLevel: 'DIRECTIONALLY_ALIGNED'
  };
}
