import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';
import { apiResponse } from '../../test/apiEnvelope';
import { OvernightStrategyPanel, ResearchResult } from './OvernightStrategyPanel';
import type { OvernightReport } from './overnightTypes';

const blocked = { mode: 'TAIL_ENTRY', instrumentCode: '605058.SH', signalDate: '2026-09-16', cutoff: '14:30',
  status: 'DATA_UNAVAILABLE', dataThrough: '2026-09-16T14:30:00', generatedAt: '2026-09-16T14:31:00',
  costBps: 20, evidenceKind: 'FORWARD', targets: [], warnings: ['分钟历史不足'] };

test('keeps entry cutoff separate from ledger holding research and never fabricates missing probabilities', async () => {
  const requests: Array<Record<string, unknown>> = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).endsWith('/automation')) {
      return apiResponse({ enabled: true, candidateLimit: 6, serverTime: '2026-09-23T12:00:00', jobs: [], calendarAvailable: true });
    }
    if (String(input).endsWith('/capture')) {
      return apiResponse({ plan: { enabled: false, instrumentCodes: [], costBps: 20 }, runs: [], slots: [], serverTime: '2026-09-23T12:00:00', calendarAvailable: true });
    }
    if (String(input).endsWith('/validation')) {
      return apiResponse({ groups: [], recordCount: 0, limitations: [] });
    }
    if (String(input).endsWith('/history')) {
      return apiResponse([]);
    }
    if (String(input).endsWith('/stock-account')) {
      return apiResponse({ positions: [{ instrumentCode: '605058.SH', instrumentName: '澳弘电子', averageCost: 20, quantity: 100, openedOn: '2026-09-15' }] });
    }
    const body = JSON.parse(String(init?.body));
    requests.push(body);
    return apiResponse({ ...blocked, ...body });
  }));
  const user = userEvent.setup();
  render(<OvernightStrategyPanel />);
  expect(screen.queryByLabelText('研究股票')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: '研究工具 ↗' }));
  await user.type(screen.getByLabelText('研究股票'), '605058');
  await user.selectOptions(screen.getByLabelText('数据截止时刻'), '14:50');
  await user.click(screen.getByRole('button', { name: '生成尾盘入场研究' }));
  expect(await screen.findByText('分钟数据未就绪')).toBeInTheDocument();
  expect(screen.queryByText('50.0%')).not.toBeInTheDocument();
  expect(requests[0]).toMatchObject({ mode: 'TAIL_ENTRY', cutoff: '14:50' });
  await user.click(screen.getByRole('button', { name: '关闭详情' }));
  await user.click(screen.getByRole('button', { name: /盘后持仓 已经持有/ }));
  await user.click(screen.getByRole('button', { name: '研究工具 ↗' }));
  expect(await screen.findByText('账本成本 ¥20.00')).toBeInTheDocument();
  expect(screen.getByLabelText('数据截止时刻')).toBeDisabled();
  await user.click(screen.getByRole('button', { name: '生成盘后持仓研究' }));
  await waitFor(() => expect(requests).toHaveLength(2));
  expect(requests[1]).toMatchObject({ mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00' });
  expect(requests[1]).not.toHaveProperty('costBasis');
});

test('shows both frozen modes and settles observations without generating another prediction', async () => {
  const calls: string[] = [];
  const records = [{ ...blocked, id: 'tail' }, { ...blocked, id: 'holding', mode: 'AFTER_CLOSE_HOLDING', cutoff: '15:00' }];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    calls.push(String(input));
    if (String(input).endsWith('/stock-account')) {
      return apiResponse({ positions: [] });
    }
    return apiResponse(records);
  }));
  const user = userEvent.setup();
  render(<OvernightStrategyPanel />);
  await user.click(screen.getByRole('button', { name: '历史复盘' }));
  await waitFor(() => expect(screen.getByLabelText('两类预测独立档案').querySelectorAll('.overnight-review-row')).toHaveLength(1));
  expect(screen.getByText(/当前场景与范围共 1 份/)).toBeVisible();
  await user.click(screen.getByRole('button', { name: '更新到期结果' }));
  await waitFor(() => expect(calls).toContain('/api/quant/overnight/settle'));
  expect(calls).not.toContain('/api/quant/overnight/generate');
});

test('shows missing direction instead of confusing legacy profitability with next-day growth', () => {
  const report: OvernightReport = { ...blocked, mode: 'TAIL_ENTRY', evidenceKind: 'FORWARD',
    modelVersion: 'legacy', inputFingerprint: 'old', targets: [{ target: '10:00', status: 'WATCH',
      sampleCount: 100, upProbability: .75, expectedNetReturn: .02 }] };
  render(<ResearchResult report={report} />);
  expect(screen.getByText('这份记录没有可用的次日涨跌预测。')).toBeVisible();
  expect(screen.queryByText('75.0%')).not.toBeInTheDocument();
  expect(screen.queryByText('费用假设')).not.toBeInTheDocument();
  expect(screen.queryByText(/净收益/)).not.toBeInTheDocument();
});
