import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { OvernightAutomationPanel } from './OvernightAutomationPanel';
import type { OvernightAutomationState } from './overnightTypes';

const state: OvernightAutomationState = {
  enabled: true, candidateLimit: 6, serverTime: '2026-09-21T14:31:00', calendarAvailable: true, tradingDay: true,
  nextTailAt: '2026-09-21T14:45:00', ledgerFresh: true, positionCount: 1, holdingStatus: 'WAITING_CLOSE',
  heartbeat: { lastTickAt: '2026-09-21T14:30:50' }, jobs: [{ key: 'tail', signalDate: '2026-09-21',
    cutoff: '14:30', mode: 'TAIL_ENTRY', phase: 'PREDICT', status: 'COMPLETED', startedAt: '2026-09-21T14:30:00', attempts: 1,
    candidates: [{ instrumentCode: '605058.SH', instrumentName: '澳弘电子', changePct: 5.2, volumeRatio: 2 }],
    results: [{ instrumentCode: '605058.SH', status: 'INSUFFICIENT_DATA', warnings: ['有效样本仅 26 例'] }] }],
};

test('presents automatically discovered results without requiring a code or action', async () => {
  const fetcher = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => apiResponse(state));
  vi.stubGlobal('fetch', fetcher);
  render(<OvernightAutomationPanel mode="TAIL_ENTRY" records={[]} renderReport={() => null} />);
  expect(await screen.findByText('澳弘电子')).toBeVisible();
  expect(screen.getByText('样本积累中')).toBeVisible();
  expect(screen.getByText('自动研究已开启')).toBeVisible();
  expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  expect(screen.queryByText('50.0%')).not.toBeInTheDocument();
  expect(screen.queryByText(/有效样本仅/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByText('澳弘电子'));
  expect(screen.getByText(/有效样本仅/)).toBeVisible();
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(fetcher.mock.calls[0][0]).toBe('/api/quant/overnight/automation');
  expect(fetcher.mock.calls[0][1]?.method).toBeUndefined();
});

test('distinguishes a stale ledger from a genuinely empty account and keeps old jobs dated', async () => {
  let current = { ...state, ledgerFresh: false, holdingStatus: 'WAITING_LEDGER', jobs: [] };
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse(current)));
  const view = render(<OvernightAutomationPanel mode="AFTER_CLOSE_HOLDING" records={[]} renderReport={() => null} />);
  expect(await screen.findByText(/账本尚未同步或已过期/)).toBeVisible();
  expect(screen.queryByText(/账本当前没有未平仓股票/)).not.toBeInTheDocument();
  view.unmount();
  current = { ...current, ledgerFresh: true, positionCount: 0, holdingStatus: 'EMPTY' };
  render(<OvernightAutomationPanel mode="AFTER_CLOSE_HOLDING" records={[]} renderReport={() => null} />);
  expect(await screen.findByText(/账本当前没有未平仓股票/)).toBeVisible();
});

test('exposes missed windows and unknown calendars without claiming successful predictions', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse({ ...state, calendarAvailable: false, jobs: [{ ...state.jobs[0],
    status: 'MISSED', reason: '决策前没有有效候选快照', candidates: [], results: [] }] })));
  render(<OvernightAutomationPanel mode="TAIL_ENTRY" records={[]} renderReport={() => null} />);
  expect(await screen.findByText(/交易日历未覆盖下一窗口/)).toBeVisible();
  expect(screen.getByText('错过窗口')).toBeVisible();
  expect(screen.getByText('14:30 错过窗口：决策前没有有效候选快照')).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: '运行状态 ↗' }));
  expect(screen.getAllByText('错过窗口').length).toBeGreaterThan(0);
  expect(screen.getByText('决策前没有有效候选快照')).toBeVisible();
});

test('refreshes automatically and cleans up polling when leaving the page', async () => {
  const fetcher = vi.fn(async () => apiResponse(state));
  vi.stubGlobal('fetch', fetcher);
  vi.useFakeTimers();
  try {
    const view = render(<OvernightAutomationPanel mode="TAIL_ENTRY" records={[]} renderReport={() => null} />);
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(fetcher).toHaveBeenCalledTimes(2);
    view.unmount();
    await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
    expect(fetcher).toHaveBeenCalledTimes(2);
  } finally {
    vi.useRealTimers();
  }
});

test('does not claim healthy background execution without a worker heartbeat', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse({ ...state, heartbeat: undefined })));
  render(<OvernightAutomationPanel mode="TAIL_ENTRY" records={[]} renderReport={() => null} />);
  await waitFor(() => expect(screen.getByText('等待自动任务恢复')).toBeVisible());
  expect(screen.getByText('澳弘电子')).toBeVisible();
  expect(screen.queryByText('后台运行')).not.toBeInTheDocument();
});

test('shows historical coverage separately from predictive accuracy and exposes backfill failures', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => apiResponse({ ...state, history: { desiredDays: 140,
    coverage: [{ instrumentCode: '605058.SH', completeDays: 181, barCount: 8688, firstDate: '2026-01-05', lastDate: '2026-09-30' }],
    jobs: [{ key: 'history', instrumentCode: '600000.SH', status: 'FAILED', reason: '历史源价格冲突，未合并' }],
  } })));
  render(<OvernightAutomationPanel mode="TAIL_ENTRY" records={[]} renderReport={() => null} />);
  await screen.findByText('澳弘电子');
  await userEvent.click(screen.getByRole('button', { name: '模型研究 ↗' }));
  expect(await screen.findByText('1 只已覆盖 140 个完整交易日')).toBeVisible();
  expect(screen.getByText('历史源价格冲突，未合并')).not.toBeVisible();
  await userEvent.click(screen.getByText('历史样本自动补齐'));
  expect(screen.getByText('181 个完整交易日')).toBeVisible();
  expect(screen.getByText('历史源价格冲突，未合并')).toBeVisible();
  expect(screen.getByText(/历史补数不计作前瞻预测成绩/)).toBeVisible();
});
