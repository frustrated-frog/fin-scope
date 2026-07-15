import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';

import { CapitalHistoricalEvaluationCard } from './CapitalHistoricalEvaluationCard';
import type { CapitalBehaviorEvaluation } from './marketIntelTypes';

const evaluation: CapitalBehaviorEvaluation = {
  id: 1,
  snapshotId: 12,
  asOf: '2026-07-14T15:00:00',
  dataFrom: '2026-06-20',
  dataTo: '2026-07-14',
  evaluationVersion: 'capital-evaluation-v1',
  factorVersion: 'capital-factor-v1',
  signalVersion: 'capital-signal-v2',
  status: 'AVAILABLE',
  dailySampleCount: 20,
  evaluableEventCount: 11,
  coverageRate: 0.9,
  missingLossRate: 0.1,
  signals: [{
    signalType: 'AMOUNT_EXPANSION_WITH_INFLOW',
    signalLabel: '放量流入',
    horizonDays: 3,
    sampleCount: 8,
    averageReturn: 0.0125,
    medianReturn: 0.01,
    positiveRate: 0.625,
    averageMfe: 0.02,
    averageMae: -0.008,
    stabilityStatus: 'INSUFFICIENT_SAMPLE',
    evaluationStatus: 'EXPLORATORY',
    lastEventDate: '2026-07-09'
  }, {
    signalType: 'PRICE_FLOW_DIVERGENCE',
    signalLabel: '价资背离',
    horizonDays: 5,
    sampleCount: 3,
    stabilityStatus: 'INSUFFICIENT_SAMPLE',
    evaluationStatus: 'UNTESTED',
    lastEventDate: '2026-07-08'
  }],
  dataGaps: ['有 1 个事件的价格标签缺失，未纳入历史统计。']
};

test('separates exploratory statistics from samples below the publication gate', () => {
  render(<CapitalHistoricalEvaluationCard evaluation={evaluation} />);

  expect(screen.getByRole('heading', { name: '历史表现校验' })).toBeInTheDocument();
  expect(screen.getByText('探索性统计')).toBeInTheDocument();
  expect(screen.getByText('平均收益')).toBeInTheDocument();
  expect(screen.getByText('+1.25%')).toBeInTheDocument();
  expect(screen.getByText('样本不足，暂不展示收益比例')).toBeInTheDocument();
  expect(screen.getByText('历史统计仅描述样本，不代表未来表现。')).toBeInTheDocument();
});

test('gives a direct next action when an old snapshot has no evaluation', () => {
  render(<CapitalHistoricalEvaluationCard evaluation={null} />);

  expect(screen.getByText('当前快照尚无历史评价')).toBeInTheDocument();
  expect(screen.getByText(/刷新资金数据后生成/)).toBeInTheDocument();
});

test('never publishes statistics from an invalidated evaluation', () => {
  const invalidated: CapitalBehaviorEvaluation = {
    ...evaluation,
    signals: [{
      ...evaluation.signals[0],
      evaluationStatus: 'INVALIDATED',
      sampleCount: 12
    }]
  };

  render(<CapitalHistoricalEvaluationCard evaluation={invalidated} />);

  expect(screen.getByText('已失效')).toBeInTheDocument();
  expect(screen.queryByText('平均收益')).not.toBeInTheDocument();
  expect(screen.getByText('该统计已失效，暂不展示收益比例')).toBeInTheDocument();
});
