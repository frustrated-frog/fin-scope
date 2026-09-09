import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { NextSessionForecast } from './NextSessionForecast';

const prediction = {
  status: 'READY' as const, asOfDate: '2026-09-04', targetDate: '2026-09-07', generatedAt: '2026-09-04T16:00:00',
  label: 'NEXT_CLOSE_RETURN', lastClose: 10, upProbability: .65, expectedReturn: .01,
  lowerReturn: -.02, upperReturn: .03, decision: 'UP' as const, modelVersion: 'next-session-rolling-v1',
  dataFingerprint: 'a'.repeat(64), trainingThrough: '2026-06-01', calibrationThrough: '2026-09-04',
  trainingSampleCount: 504, calibrationSampleCount: 60, validationSampleCount: 60,
  brierScore: .22, baselineBrierScore: .25, accuracy: .6, intervalCoverage: .8, warnings: [],
};

test('shows explicit target and close-to-close evidence without claiming trading profit', () => {
  render(<NextSessionForecast prediction={prediction} />);
  expect(screen.getByText('2026-09-07')).toBeInTheDocument();
  expect(screen.getByText('65.0%')).toBeInTheDocument();
  expect(screen.getByText(/收盘相对/)).toBeInTheDocument();
  expect(screen.getByText(/不等于可成交收益/)).toBeInTheDocument();
});

test('does not manufacture probabilities for missing or stale evidence', () => {
  render(<NextSessionForecast prediction={{ ...prediction, status: 'STALE_DATA', upProbability: undefined,
    expectedReturn: undefined, lowerReturn: undefined, upperReturn: undefined }} />);
  expect(screen.getByText('行情已过期')).toBeInTheDocument();
  expect(screen.queryByText('65.0%')).not.toBeInTheDocument();
});

test('keeps joint shadow probabilities separate and never calls ranking a probability', () => {
  const jointModel = {
    modelVersion: 'joint-v1', selectedClassifier: 'LIGHTGBM', applied: false,
    classificationEligible: false, rankingEligible: true, featureCount: 38, universeCount: 183,
    trainingSampleCount: 20000, validationSampleCount: 10000, validationDayCount: 60,
    testStart: '2026-06-10', testEnd: '2026-09-04', selectionBrierScore: .24, selectionRankIc: .02,
    pooledBrierScore: .24, baselineBrierScore: .25, logisticBrierScore: .245, accuracy: .55,
    intervalCoverage: .8, regressionMse: .001, baselineRegressionMse: .002, rankIc: .03,
    top5Return: .002, top5PoolExcess: .001, top5MomentumExcess: .001, rankingScore: 1.2,
    rankingPercentile: .9, stockValidationCount: 60, stockBrierScore: .26, stockBaselineBrierScore: .25,
    upProbability: .7, expectedReturn: .01, reason: '该股概率尚未优于原模型',
  };
  render(<NextSessionForecast prediction={{ ...prediction, jointModel }} />);
  expect(screen.getByText('65.0%')).toBeInTheDocument();
  expect(screen.getByText('联合模型对照 · 当前保留原预测')).toBeInTheDocument();
  expect(screen.getByText(/90.0%（越高越靠前，非上涨概率）/)).toBeInTheDocument();
  expect(screen.getByText(/对照上涨概率 70.0%/)).toBeInTheDocument();
  expect(screen.getByText(/Top 5 次日平均涨跌 \+0.200%/)).toBeInTheDocument();
  expect(screen.getByText(/超过同日股票池 \+0.100 个百分点/)).toBeInTheDocument();
});

test('separates full direction accuracy from high-confidence coverage', () => {
  const directionEvaluation = {
    accuracy: .54, balancedAccuracy: .51, brierScore: .24, dayCount: 60, sampleCount: 6000,
    highConfidence: { coverage: .2, accuracy: .7 }, eligible: false,
    reason: '尚未同时优于基准', comparisons: { PRIOR: {accuracy: .55, brierScore: .245,
      accuracyDifferenceLower: -.04, accuracyDifferenceUpper: .02, brierDifferenceLower: -.01, brierDifferenceUpper: .003} },
  };
  render(<NextSessionForecast prediction={{...prediction, directionEvaluation}} />);
  expect(screen.getByText(/平衡准确率 51.0%/)).toBeInTheDocument();
  expect(screen.getByText(/覆盖 20.0%.*命中 70.0%/)).toBeInTheDocument();
  expect(screen.getByText(/60 个交易日.*6000 个股票样本/)).toBeInTheDocument();
});

test('shows calibration decision collapse separately from continuous score discrimination', () => {
  const directionEvaluation = {
    accuracy: .51, balancedAccuracy: .5, brierScore: .25, dayCount: 60, sampleCount: 6000,
    highConfidence: { coverage: 0, accuracy: null }, eligible: false, reason: '对照', comparisons: {},
    predictedUpRate: 0, auc: .53, crossSectionAuc: .52, crossSectionAucDayCount: 60,
    calibrationAudit: { raw: { accuracy: .52, balancedAccuracy: .52, brierScore: .2495, predictedUpRate: .5, auc: .53 },
      parameters: { slope: .1383, intercept: -.0342 }, trainingThrough: '2026-01-01',
      calibrationStart: '2026-03-01', calibrationThrough: '2026-06-01' },
  };
  render(<NextSessionForecast prediction={{ ...prediction, directionEvaluation }} />);
  expect(screen.getByText(/预测上涨占比 0.0%/)).toBeInTheDocument();
  expect(screen.getByText(/总体 AUC 0.530.*同日 AUC 0.520/)).toBeInTheDocument();
  expect(screen.getByText(/校准前：方向准确率 52.0%.*预测上涨占比 50.0%/)).toBeInTheDocument();
  expect(screen.getByText(/斜率 0.1383/)).toBeInTheDocument();
});
