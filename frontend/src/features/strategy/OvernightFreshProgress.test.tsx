import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { OvernightFreshProgress } from './OvernightFreshProgress';
import type { OvernightJointState } from './overnightJointTypes';

const state: OvernightJointState = { protocol: 'test', poolSize: 120, targetSize: 120, readySymbols: 20,
  minimumSymbols: 20, jobs: [], models: [{ id: 'one', createdAt: '2026-11-06', labelsThrough: '2026-11-06',
    profile: { key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', costBps: 20 },
    data: { symbolCount: 20, dayCount: 200, rows: 4000 }, targets: [],
    freshResearch: { protocol: 'overnight-fresh-direction-v1', status: 'SHADOW', models: {
      FRESH: { audit: { trainingThrough: '2026-11-05T15:00:00', legacyTrainingThrough: '2026-09-29T15:00:00' } },
    } } }] };

test('shows actual training freshness without claiming an improvement or inventing missing scores', () => {
  const { rerender } = render(<OvernightFreshProgress state={state} mode="TAIL_ENTRY" />);
  expect(screen.getByText(/新方案学习至 2026-11-05，旧方案至 2026-09-29/)).toBeVisible();
  expect(screen.getAllByText('等待真实次日结果')).toHaveLength(2);
  expect(screen.getAllByText('数据积累中')).toHaveLength(2);
  expect(screen.getByText('近期预测重新择模')).toBeVisible();
  expect(screen.queryByText(/0.0%/)).not.toBeInTheDocument();
  rerender(<OvernightFreshProgress state={state} mode="AFTER_CLOSE_HOLDING" />);
  expect(screen.queryByRole('region', { name: '新训练方案对照' })).not.toBeInTheDocument();
  rerender(<OvernightFreshProgress state={{ ...state, models: [] }} mode="TAIL_ENTRY" />);
  expect(screen.queryByRole('region', { name: '新训练方案对照' })).not.toBeInTheDocument();
  rerender(<OvernightFreshProgress state={{ ...state, models: [], freshForward: {
    protocol: 'overnight-fresh-direction-v1', groups: [] } }} mode="TAIL_ENTRY" />);
  expect(screen.getByText(/新训练方案尚未就绪/)).toBeVisible();
});

test('displays paired real evidence and rejects incompatible versions', () => {
  const result = { protocol: 'overnight-fresh-direction-v1', groups: [{ mode: 'TAIL_ENTRY' as const,
    cutoff: '14:30', group: 'FRESH', dayCount: 2, pairedCount: 6, scheduledCount: 9,
    metrics: { accuracy: .5, comparisons: { MATCHED_BASELINE: { accuracy: .6667 } } } }] };
  const { rerender } = render(<OvernightFreshProgress state={{ ...state, freshForward: result }} mode="TAIL_ENTRY" />);
  expect(screen.getByText(/50.0% \/ 66.7%/)).toHaveTextContent('已配对 6 / 9 条 · 此处仅为对照统计');
  rerender(<OvernightFreshProgress state={{ ...state, freshForward: { ...result, protocol: 'old' } }} mode="TAIL_ENTRY" />);
  expect(screen.queryByText(/50.0%/)).not.toBeInTheDocument();
});
