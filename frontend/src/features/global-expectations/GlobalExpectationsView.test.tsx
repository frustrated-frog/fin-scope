import { render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { GlobalExpectationsView } from './GlobalExpectationsView';

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, code: 'SUCCESS', message: 'success', traceId: 'trace', timestamp: '2026-08-15T00:00:00Z', data: [{ id: 1, theme: '科技供应链', question: '美国是否会在今年进一步扩大 AI 芯片出口限制？', marketUrl: 'https://polymarket.com', probability: 62, change5m: 2.4, change1h: 4.2, change24h: 9.4, volume: 1284000, openInterest: 482000, spread: 2, endDate: '2026-12-31', observation: '观察限制范围、具体产品与美国商务部正式文件。', status: 'SIGNAL', dataStatus: 'LIVE', observedAt: '刚刚', lastRefreshAt: '刚刚', priceHistory: [{ observedAt: '09:00', probability: 58 }, { observedAt: '09:05', probability: 62 }] }] })
  }));
});

test('presents probability movement as a research lead instead of a trading call', async () => {
  render(<GlobalExpectationsView addToast={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: /美国是否会在今年进一步扩大 AI 芯片出口限制/ })).toBeInTheDocument();
  expect(screen.getByText(/重新定价什么/)).toBeInTheDocument();
  expect(screen.getByText('待核验 · 刚刚')).toBeInTheDocument();
});

test('opens a detail view with multiple observation windows', async () => {
  render(<GlobalExpectationsView addToast={vi.fn()} />);

  expect(await screen.findByText('5m')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /查看变化详情/ })).toBeInTheDocument();
});
