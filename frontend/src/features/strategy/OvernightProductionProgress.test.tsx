import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { OvernightProductionProgress } from './OvernightProductionProgress';
import type { OvernightJointState } from './overnightJointTypes';

const state: OvernightJointState = { protocol: 'test', poolSize: 120, targetSize: 120, readySymbols: 120,
  minimumSymbols: 20, models: [], jobs: [], productionDirection: { protocol: 'overnight-production-direction-v1',
    target: 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE', computedAt: '2026-11-12T19:00:00', groups: [
      { key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', group: 'ROLLING', status: 'ACCUMULATING', eligible: false,
        dayCount: 4, requiredDays: 60, recentCoverage: .8, checkpointPassed: false },
      { key: 'holding', mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00', group: 'ADAPTIVE', status: 'QUALIFIED', eligible: true,
        dayCount: 63, requiredDays: 60, recentCoverage: 1, checkpointPassed: true },
    ] } };

test('separates real day progress, qualification and actual adoption by mode', () => {
  const { rerender } = render(<OvernightProductionProgress state={state} mode="TAIL_ENTRY" />);
  expect(screen.getByRole('progressbar')).toHaveAttribute('value', '4');
  expect(screen.getByText('等待真实结果')).toBeVisible();
  expect(screen.queryByText('达到启用条件')).not.toBeInTheDocument();
  rerender(<OvernightProductionProgress state={state} mode="AFTER_CLOSE_HOLDING" />);
  expect(screen.getByText('达到启用条件')).toBeVisible();
  expect(screen.getByText(/是否采用以该次记录为准/)).toBeVisible();
  expect(screen.getByRole('progressbar')).toHaveAttribute('value', '60');
  expect(screen.queryByText(/14:30/)).not.toBeInTheDocument();
});

test('explains frozen failure and degradation without declaring an active model', () => {
  const monitor = state.productionDirection!;
  const group = monitor.groups[0];
  const { rerender } = render(<OvernightProductionProgress state={{ ...state, productionDirection: { ...monitor,
    groups: [{ ...group, status: 'CHECKPOINT_FAILED', dayCount: 75 }] } }} mode="TAIL_ENTRY" />);
  expect(screen.getByText(/后续好成绩不会覆盖失败结果/)).toBeVisible();
  rerender(<OvernightProductionProgress state={{ ...state, productionDirection: { ...monitor,
    groups: [{ ...group, status: 'MONITORING_DEGRADED', checkpointPassed: true }] } }} mode="TAIL_ENTRY" />);
  expect(screen.getByText('近期退化，暂停采用')).toBeVisible();
  rerender(<OvernightProductionProgress state={{ ...state, productionDirection: { ...monitor, groups: [] } }} mode="TAIL_ENTRY" />);
  expect(screen.getByText(/等待新版预测的次日结果/)).toBeVisible();
  expect(screen.queryByText(/0%/)).not.toBeInTheDocument();
});
