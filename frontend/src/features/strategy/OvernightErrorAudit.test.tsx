import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { OvernightErrorAudit } from './OvernightErrorAudit';
import type { ErrorAudit } from './overnightErrorTypes';

const candidates = { precision: null, selectedDays: 0, dayCount: 0 };
const score = { sampleCount: 2, dayCount: 1, accuracy: .5, falseUpCount: 1, missedUpCount: 0, status: 'SMALL_SAMPLE' };
const audit: ErrorAudit = { target: 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE', computedAt: '2026-11-05T16:30:00', groups: [{
  key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', scheduledCount: 3, settledCount: 2,
  coverage: 2/3, completeDays: 0, missing: { MISSING_PREDICTION: 1 }, metrics: score,
  upCandidates: candidates, slices: [{ ...score, dimension: 'marketState', value: 'UNKNOWN' }],
  selectionComparison: { pairedDays: 0, incumbent: candidates, challenger: candidates },
}] };

test('exposes incomplete candidate coverage without inventing a hit rate', async () => {
  render(<OvernightErrorAudit audit={audit} mode="TAIL_ENTRY" />);
  expect(screen.getByText('2 / 3')).toBeVisible();
  expect(screen.getByText('0 个完整核验日')).toBeVisible();
  expect(screen.getByText('看涨前 3 只 · 实际上涨比例')).toBeVisible();
  expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
  expect(screen.getByText('没有按时预测：1 只次')).not.toBeVisible();
  await userEvent.click(screen.getByText('查看错判分布与数据缺口'));
  expect(screen.getByText('没有按时预测：1 只次')).toBeVisible();
  expect(screen.getByText('研究池状态 · 缺少记录')).toBeVisible();
  expect(screen.getByText('1 日 / 2 例 · 小样本')).toBeVisible();
});

test('keeps holdings and old targets out of tail diagnostics', () => {
  const { rerender } = render(<OvernightErrorAudit audit={audit} mode="AFTER_CLOSE_HOLDING" />);
  expect(screen.queryByText('14:30 判断')).not.toBeInTheDocument();
  rerender(<OvernightErrorAudit audit={{ ...audit, target: 'OLD' }} mode="TAIL_ENTRY" />);
  expect(screen.getByText(/等待已到期的候选/)).toBeVisible();
});

test('shows paired ranking results only after complete forward dates', async () => {
  const ranking: NonNullable<ErrorAudit['groups'][number]['rankingComparison']> = { pairedDays: 0, pairedRows: 0, missingPairedDays: 2,
    results: { RANKER: candidates, CLASSIFIER: candidates, MOMENTUM: candidates, UNIFORM: candidates } };
  const value: ErrorAudit = { ...audit, groups: [{ ...audit.groups[0], rankingComparison: ranking }] };
  const { rerender } = render(<OvernightErrorAudit audit={value} mode="TAIL_ENTRY" />);
  await userEvent.click(screen.getByText('查看错判分布与数据缺口'));
  expect(screen.getByText(/等待新版排序与分类预测同时留档/)).toBeVisible();
  expect(screen.getByText(/2 个结果完整日缺少整批配对预测/)).toBeVisible();
  expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
  ranking.pairedDays = 1;
  ranking.pairedRows = 6;
  ranking.results.RANKER = { ...candidates, precision: .5 };
  rerender(<OvernightErrorAudit audit={value} mode="TAIL_ENTRY" />);
  expect(screen.getByText('同输入分类')).toBeVisible();
  expect(screen.getByText(/1 个完整同日对照 · 6 只次/)).toBeVisible();
  expect(screen.getByText(/不是单只股票的上涨概率/)).toBeVisible();
});
