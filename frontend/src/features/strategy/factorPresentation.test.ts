import { describe, expect, test } from 'vitest';
import {
  datasetEvidenceNotice,
  explainFactorAnalysis,
  lifecycleLabel,
  researchDirectionLabel
} from './factorPresentation';

describe('factor presentation', () => {
  test('describes lifecycle and direction as research status rather than proof', () => {
    expect(lifecycleLabel('CALCULATION_VERIFIED')).toBe('计算已核验');
    expect(lifecycleLabel('EXPLORATORY')).toBe('探索中');
    expect(researchDirectionLabel('POSITIVE_HYPOTHESIS')).toContain('默认研究方向');
    expect(researchDirectionLabel('POSITIVE_HYPOTHESIS')).toContain('假设');
  });

  test('never treats a learning dataset as market validation evidence', () => {
    expect(datasetEvidenceNotice('LEARNING_SAMPLE')).toContain('只能学习研究流程');
    expect(datasetEvidenceNotice('LEARNING_SAMPLE')).toContain('不能证明真实市场有效性');
  });

  test('adjusts IC interpretation for a low-direction factor', () => {
    const explanation = explainFactorAnalysis({
      datasetId: 1,
      datasetFingerprint: 'fingerprint',
      factorCode: 'LOG_MARKET_CAP',
      sampleCount: 80,
      icMean: -0.06,
      icStd: 0.12,
      icIr: -0.5,
      positiveIcRatio: 0.28
    }, 'NEGATIVE_HYPOTHESIS', 'REAL');

    expect(explanation.directionAdjustedIcMean).toBeCloseTo(0.06);
    expect(explanation.favorableIcRatio).toBeCloseTo(0.72);
    expect(explanation.headline).toContain('预设方向较一致');
    expect(explanation.detail).toContain('低值方向');
    expect(explanation.detail).not.toContain('有效');
  });

  test('calls out insufficient and unstable evidence without overclaiming', () => {
    const tooSmall = explainFactorAnalysis({
      datasetId: 1, datasetFingerprint: 'x', factorCode: 'EP', sampleCount: 8,
      icMean: 0.15, icStd: 0.2, icIr: 0.75, positiveIcRatio: 0.75
    }, 'POSITIVE_HYPOTHESIS', 'REAL');
    expect(tooSmall.headline).toContain('样本不足');

    const unstable = explainFactorAnalysis({
      datasetId: 1, datasetFingerprint: 'x', factorCode: 'EP', sampleCount: 80,
      icMean: 0.005, icStd: 0.2, icIr: 0.025, positiveIcRatio: 0.49
    }, 'POSITIVE_HYPOTHESIS', 'REAL');
    expect(unstable.headline).toContain('方向不稳定');
    expect(unstable.detail).toContain('不是个股涨跌预测');
  });
});
