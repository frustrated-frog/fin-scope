import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';
import { OvernightTrainingStatus } from './OvernightTrainingStatus';
import type { OvernightJointState } from './overnightJointTypes';

const state: OvernightJointState = { protocol: 'test', trainingPolicy: 'new', poolSize: 120, targetSize: 120,
  readySymbols: 120, minimumSymbols: 20, models: [{ id: 'old', createdAt: '2026-11-14T00:10:00',
    labelsThrough: '2026-11-13T15:00:00', profile: { key: 'one', mode: 'TAIL_ENTRY', cutoff: '14:30', costBps: 20 },
    data: { symbolCount: 120, dayCount: 200, rows: 20000 }, targets: [] }], jobs: [
    { key: 'z-old', mode: 'TAIL_ENTRY', cutoff: '14:30', status: 'COMPLETED', startedAt: '2026-11-14T00:00:00' },
    { key: 'a-new', mode: 'TAIL_ENTRY', cutoff: '14:30', status: 'RUNNING', startedAt: '2026-11-14T00:29:00',
      trainingStage: 'CANDIDATE', progressAt: '2026-11-14T00:32:00', componentStatus: {
        DIRECTION: { status: 'READY' }, CANDIDATE: { status: 'RUNNING' }, MARKET: { status: 'INSUFFICIENT_DATA' } } },
  ] };

test('shows actual training stage despite unsorted older jobs and keeps serving model distinct', async () => {
  render(<OvernightTrainingStatus state={state} mode="TAIL_ENTRY" />);
  expect(screen.getByText('0 / 2 个时点新版就绪')).toBeVisible();
  await userEvent.click(screen.getByText('训练状态'));
  expect(screen.getByText('14:30 · 逐日回放候选')).toBeVisible();
  expect(screen.getByText('当前保留上一版本')).toBeVisible();
  expect(screen.getByText(/已结束 2 项检查与训练/)).toBeVisible();
  expect(screen.getByText(/最近进度 2026-11-14 00:32:00/)).toBeVisible();
  expect(state.jobs[0].key).toBe('z-old');
});

test('model readiness needs the current policy and a complete artifact', async () => {
  const updated = { ...state, models: [{ ...state.models[0], trainingPolicy: 'new', trainingStatus: 'COMPLETED' }], jobs: [] };
  const { rerender } = render(<OvernightTrainingStatus state={updated} mode="TAIL_ENTRY" />);
  expect(screen.getByText('1 / 2 个时点新版就绪')).toBeVisible();
  await userEvent.click(screen.getByText('训练状态'));
  expect(screen.getByText('14:30 · 新版模型已就绪')).toBeVisible();
  expect(screen.getByText(/新方案是否用于某次判断，仍由独立前瞻验收决定/)).toBeVisible();
  rerender(<OvernightTrainingStatus state={{ ...updated, models: [{ ...updated.models[0], trainingStatus: 'PARTIAL' }] }} mode="TAIL_ENTRY" />);
  expect(screen.getByText('0 / 2 个时点新版就绪')).toBeVisible();
  rerender(<OvernightTrainingStatus state={updated} mode="AFTER_CLOSE_HOLDING" />);
  expect(screen.getByText('0 / 1 个时点新版就绪')).toBeVisible();
  expect(screen.queryByText(/14:30/)).not.toBeInTheDocument();
});

test('missing stage reports stay unknown instead of displaying zero finished work', async () => {
  render(<OvernightTrainingStatus state={{ ...state, jobs: [{ ...state.jobs[1], componentStatus: undefined,
    progressAt: undefined, trainingStage: undefined }] }} mode="TAIL_ENTRY" />);
  await userEvent.click(screen.getByText('训练状态'));
  expect(screen.getByText('等待训练阶段回报。')).toBeVisible();
  expect(screen.queryByText(/已结束 0/)).not.toBeInTheDocument();
});
