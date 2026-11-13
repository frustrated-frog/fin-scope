import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { OvernightCandidateProgress } from './OvernightCandidateProgress';
import { candidateProtocol } from './overnightCandidateTypes';
import type { OvernightJointState } from './overnightJointTypes';

const state: OvernightJointState = { protocol: 'test', poolSize: 120, targetSize: 120, readySymbols: 120,
  minimumSymbols: 20, jobs: [], models: [{ id: 'new', createdAt: '2026-11-12T08:00:00', labelsThrough: '2026-11-11T15:00:00',
    profile: { key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', costBps: 0 }, data: { symbolCount: 120, dayCount: 180, rows: 20000 }, targets: [],
    upSelection: { protocol: candidateProtocol, audit: { modelFitThrough: '2026-11-11T15:00:00', selection: {
      reason: 'NO_CANDIDATE_ADVANTAGE', selectionDays: 14, plannedDays: 14, candidateCoverage: 1, selectionThrough: '2026-11-11T15:00:00' } } },
    candidateReadiness: { computedAt: '2026-11-12T08:00:00', completeDays: 14, cohortDays: 15, reasonCounts: { AVAILABLE: 108, NOT_DUE: 18 },
      days: [{ signalDate: '2026-11-11', complete: false, expectedCount: 18, availableCount: 0, missing: { NOT_DUE: 18 } }] } }],
};

test('separates usable data from evidence of selection skill and exposes pending causes', async () => {
  render(<OvernightCandidateProgress state={state} />);
  expect(screen.getByText('尚未找到稳定选涨优势')).toBeVisible();
  expect(screen.getByText('14 / 15 日')).toBeVisible();
  expect(screen.getByText('14 / 14 日')).toBeVisible();
  expect(screen.getByText('0 日')).toBeVisible();
  expect(screen.getByText(/最终拟合至 2026-11-11/)).toBeVisible();
  expect(screen.getByText('次日尚未到期 18')).not.toBeVisible();
  await userEvent.click(screen.getByText('查看样本缺口与同名单对照'));
  expect(screen.getByText('次日尚未到期 18')).toBeVisible();
  expect(screen.getByText(/已配对 0 \/ 0 条/)).toBeVisible();
  expect(screen.queryByText('0.0%')).not.toBeInTheDocument();
});

test('does not present an old protocol or retrospective fit as current forward success', () => {
  const old = { ...state, models: [{ ...state.models[0], upSelection: { ...state.models[0].upSelection!, protocol: 'old' } }] };
  const { rerender } = render(<OvernightCandidateProgress state={old} />);
  expect(screen.queryByRole('region', { name: '真实候选滚动择模' })).not.toBeInTheDocument();
  rerender(<OvernightCandidateProgress state={{ ...state, candidateForward: { protocol: candidateProtocol, groups: [
    { mode: 'TAIL_ENTRY', cutoff: '14:30', dayCount: 1, pairedCount: 18, scheduledCount: 18,
      ranking: { topK: 3, dayCount: 1, noCandidateDays: 0,
        forced: { selectedDays: 1, precision: { ROLLING: 2/3, INCUMBENT: 1/3, MOMENTUM: 1/3, UNIFORM: .5 } },
        matched: { selectedDays: 1, precision: { ROLLING: 1, INCUMBENT: .5, MOMENTUM: .5, UNIFORM: .5 } } } },
  ] } }} />);
  expect(screen.getByText('1 日')).toBeVisible();
  expect(screen.getByText('66.7%')).not.toBeVisible();
});

test('exposes a failed preparation without fabricating zero accuracy or hiding the model', () => {
  render(<OvernightCandidateProgress state={{ ...state, models: [{ ...state.models[0], upSelection: null,
    candidateReadiness: { status: 'FAILED', computedAt: '2026-11-12T08:00:00', reason: '候选滚动择模失败，等待下次训练' } }] }} />);
  expect(screen.getByText('滚动择模准备失败')).toBeVisible();
  expect(screen.getByText('候选滚动择模失败，等待下次训练')).toBeVisible();
  expect(screen.queryByText('0 日')).not.toBeInTheDocument();
});

test('exposes training population mismatch without treating it as prediction accuracy', async () => {
  render(<OvernightCandidateProgress state={{ ...state, models: [{ ...state.models[0],
    candidateReadiness: { ...state.models[0].candidateReadiness!, support: {
      protocol: 'overnight-candidate-support-v1', status: 'SHIFT_DETECTED', dayCount: 14, poolRows: 1600,
      candidateRows: 108, outsidePoolRows: 106, unpairedDays: 0, features: [
        { code: 'INTRADAY_RETURN', poolMedian: -.0016, candidateMedian: .0634, outsideCentralRangeRate: .65 },
        { code: 'RELATIVE_AMOUNT_5', poolMedian: 0, candidateMedian: Math.log(3.5), outsideCentralRangeRate: .72 },
      ] } } }] }} />);
  expect(screen.getByText('候选特征与训练池存在明显差异')).not.toBeVisible();
  await userEvent.click(screen.getByText('查看样本缺口与同名单对照'));
  expect(screen.getByText(/106 \/ 108 条候选来自原训练池之外/)).toBeVisible();
  expect(screen.getByText('3.50 倍')).toBeVisible();
  expect(screen.getByText('6.3%')).toBeVisible();
  expect(screen.getByText(/不会据此调高上涨概率/)).toBeVisible();
});
