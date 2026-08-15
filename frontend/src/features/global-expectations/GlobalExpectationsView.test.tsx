import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { GlobalExpectationsView } from './GlobalExpectationsView';

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, code: 'SUCCESS', message: 'success', traceId: 'trace', timestamp: '2026-08-15T00:00:00Z', data: [
      { id: 1, theme: '政治', question: '美国是否会在今年进一步扩大 AI 芯片出口限制？', marketUrl: 'https://polymarket.com/politics', probability: 62, change1h: 4.2, change24h: 9.4, volume: 1284000, volume24h: 386000, openInterest: 482000, spread: 2, endDate: '2026-12-31', observation: '观察正式政策文件。', status: 'SIGNAL', dataStatus: 'LIVE', observedAt: '刚刚', lastRefreshAt: '刚刚', priceHistory: [{ observedAt: '09:00', probability: 58 }, { observedAt: '09:05', probability: 62 }] },
      { id: 2, theme: '财务', question: '标普500指数年底会创新高吗？', marketUrl: 'https://polymarket.com/finance', probability: 51, volume: 985000, volume24h: 245000, observation: '观察资产价格与正式披露。', status: 'WATCHING', dataStatus: 'LIVE', observedAt: '刚刚', priceHistory: [] }
    ] })
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

  expect((await screen.findAllByText('5m')).length).toBeGreaterThan(0);
  fireEvent.click(screen.getAllByRole('button', { name: /查看变化详情/ })[0]);

  expect(screen.getByLabelText('官方价格轨迹')).toBeInTheDocument();
  expect(screen.getAllByText('暂无历史').length).toBeGreaterThan(0);
});

test('offers five official category filters and shows the daily volume ranking metric', async () => {
  render(<GlobalExpectationsView addToast={vi.fn()} />);

  expect(await screen.findByRole('button', { name: '政治' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '财务' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '地缘冲突' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '科技' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '经济' })).toBeInTheDocument();
  expect(screen.getAllByText('24h 成交')).toHaveLength(2);
  expect(screen.getByText('$386k')).toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: '财务' }));

  expect(screen.getByRole('heading', { name: '标普500指数年底会创新高吗？' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: /美国是否会在今年进一步扩大/ })).not.toBeInTheDocument();
});
