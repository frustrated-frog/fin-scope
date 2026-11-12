import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { OvernightPerformance } from './OvernightPerformance';
import type { OvernightJointState } from './overnightJointTypes';

test('keeps forward accuracy paired with its baseline, coverage and decision window', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse({ groups: [], limitations: [] })));
  const group = { key: 'tail', mode: 'TAIL_ENTRY', cutoff: '14:30', status: 'ACCUMULATING', eligible: false,
    dayCount: 4, requiredDays: 60, coverage: .8, metrics: { accuracy: .55, balancedAccuracy: .51,
      comparisons: { HISTORICAL_PRIOR: { accuracy: .58, brierScore: .25 } }, brierScore: .26, sampleCount: 24, dayCount: 4 } };
  const state: OvernightJointState = { protocol: 'test', poolSize: 120, targetSize: 120, readySymbols: 80, minimumSymbols: 20, models: [], jobs: [], closeDirectionForward: { target: 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE', groups: [group, { ...group, key: 'holding', mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00' }] } };
  render(<OvernightPerformance state={state} mode="TAIL_ENTRY" revision={0} />);
  expect(screen.getByText('55.0%')).toBeVisible();
  expect(screen.getByText('58.0%')).toBeVisible();
  expect(screen.getByText('80.0%')).toBeVisible();
  expect(screen.getByText('4 / 60')).toBeVisible();
  expect(screen.queryByText('15:00 → 次日收盘')).not.toBeInTheDocument();
  expect(screen.queryByText('通过前瞻对照')).not.toBeInTheDocument();
  expect(screen.queryByText(/净收益/)).not.toBeInTheDocument();
});

test('does not mix old close-to-close statistics into the decision-price score', () => {
  const state: OvernightJointState = { protocol: 'test', poolSize: 0, targetSize: 120, readySymbols: 0,
    minimumSymbols: 20, models: [], jobs: [], closeDirectionForward: {
      target: 'NEXT_SESSION_CLOSE_VS_SIGNAL_CLOSE', groups: [{ key: 'old', mode: 'TAIL_ENTRY', cutoff: '14:30',
        status: 'QUALIFIED', eligible: true, dayCount: 60, requiredDays: 60, coverage: 1 }] } };
  render(<OvernightPerformance state={state} mode="TAIL_ENTRY" revision={0} />);
  expect(screen.getByText('等待第一批真实结果')).toBeVisible();
  expect(screen.queryByText('通过前瞻对照')).not.toBeInTheDocument();
});

test('prioritizes this week and lets the user expand cumulative diagnostics', async () => {
  const state: OvernightJointState = { protocol: 'test', poolSize: 0, targetSize: 120, readySymbols: 0,
    minimumSymbols: 20, models: [], jobs: [], weeklyDirectionAudit: {
      protocol: 'overnight-weekly-diagnostics-v1', target: 'NEXT_SESSION_CLOSE_VS_DECISION_PRICE',
      periodStart: '2026-11-09', periodEnd: '2026-11-12', computedAt: '2026-11-12T18:42:00',
      archives: [], groups: [], windows: [],
    } };
  render(<OvernightPerformance state={state} mode="TAIL_ENTRY" revision={0} />);
  expect(screen.getByRole('region', { name: '本周看涨识别' })).toBeVisible();
  expect(screen.getByText('选出的股票，后来怎么样')).not.toBeVisible();
  await userEvent.click(screen.getByText('累计方向验证'));
  expect(screen.getByText('选出的股票，后来怎么样')).toBeVisible();
});
