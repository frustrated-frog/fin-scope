import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { GlobalExpectationsView } from './GlobalExpectationsView';

beforeEach(() => {
  const items = [
    { id: 1, marketId: 'policy-yes', eventId: 'policy-event', eventTitle: '美国科技政策预期', theme: '政治', question: '美国是否会在今年进一步扩大 AI 芯片出口限制？', marketUrl: 'https://polymarket.com/politics', probability: 62, change1h: 6.2, change24h: 9.4, volume: 1284000, volume24h: 386000, openInterest: 482000, spread: 2, rank: 1, previousRank: 4, rankChange: 3, signalScore: 82, signalReasons: ['1小时概率显著上升', '分类成交排名快速上升'], endDate: '2026-12-31', observation: '观察正式政策文件。', status: 'SIGNAL', dataStatus: 'LIVE', observedAt: '刚刚', lastRefreshAt: '刚刚', priceHistory: [{ observedAt: '09:00', probability: 58 }, { observedAt: '09:05', probability: 62 }] },
    { id: 2, marketId: 'spx-high', eventId: 'policy-event', eventTitle: '美国科技政策预期', theme: '财务', question: '标普500指数年底会创新高吗？', marketUrl: 'https://polymarket.com/finance', probability: 51, volume: 985000, volume24h: 245000, rank: 2, signalScore: 0, signalReasons: [], observation: '观察资产价格与正式披露。', status: 'WATCHING', dataStatus: 'LIVE', observedAt: '刚刚', priceHistory: [] }
  ];
  const feed = { marketCount: 2, eventCount: 1, signalCount: 1, generatedAt: '刚刚', groups: [{ id: 'event:policy-event', title: '美国科技政策预期', themes: ['政治', '财务'], status: 'SIGNAL', signalScore: 82, signalReasons: ['1小时概率显著上升', '分类成交排名快速上升'], volume24h: 631000, markets: items, radarMatches: [{ eventId: 7, title: '美国讨论扩大先进芯片出口限制', summary: '监管部门正在讨论新的限制范围。', matchScore: 72 }], interpretation: { status: 'READY', happened: '概率和成交排名同步上升。', meaning: '市场对政策收紧的预期明显增强。', relatedVariables: '先进芯片、出口管制与供应链。', nextObservation: '关注正式政策文件。' } }] };
  vi.stubGlobal('fetch', vi.fn().mockImplementation((input: RequestInfo | URL) => Promise.resolve({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, code: 'SUCCESS', message: 'success', traceId: 'trace', timestamp: '2026-08-15T00:00:00Z', data: String(input).endsWith('/feed') ? feed : items })
  })));
});

test('defaults to a deterministic event signal feed with optional local and AI enhancements', async () => {
  render(<GlobalExpectationsView addToast={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '美国科技政策预期' })).toBeInTheDocument();
  expect(screen.getByText(/重新定价什么/)).toBeInTheDocument();
  expect(screen.getByText('异动强度 82')).toBeInTheDocument();
  expect(screen.getByText('分类成交排名快速上升')).toBeInTheDocument();
  expect(screen.getByText('美国讨论扩大先进芯片出口限制')).toBeInTheDocument();
  expect(screen.getByText('市场对政策收紧的预期明显增强。')).toBeInTheDocument();
  expect(screen.getByText('2 个预测选项')).toBeInTheDocument();
});

test('opens a detail view with multiple observation windows', async () => {
  render(<GlobalExpectationsView addToast={vi.fn()} />);

  fireEvent.click(await screen.findByRole('button', { name: '分类榜' }));
  expect((await screen.findAllByText('5m')).length).toBeGreaterThan(0);
  fireEvent.click(screen.getAllByRole('button', { name: /查看变化详情/ })[0]);

  expect(screen.getByLabelText('官方价格轨迹')).toBeInTheDocument();
  expect(screen.getAllByText('暂无历史').length).toBeGreaterThan(0);
});

test('offers five official category filters and shows the daily volume ranking metric', async () => {
  render(<GlobalExpectationsView addToast={vi.fn()} />);

  fireEvent.click(await screen.findByRole('button', { name: '分类榜' }));
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
