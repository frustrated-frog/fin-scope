import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { DashboardMarketOverview } from './DashboardMarketOverview';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
vi.mock('../watchlist/WatchlistKlineDrawer', () => ({
  WatchlistKlineDrawer: ({ item, onClose }: { item: { code: string }; onClose: () => void }) => <div role="dialog">行情 {item.code}<button onClick={onClose}>关闭行情</button></div>
}));
vi.mock('../watchlist/AttributionReaderView', () => ({
  AttributionReaderView: ({ reportId, onBack }: { reportId: number; onBack: () => void }) => <div>归因报告 {reportId}<button onClick={onBack}>返回速览</button></div>
}));

let responses: Record<string, unknown>;

beforeEach(() => {
  vi.mocked(api).mockReset();
  responses = {
    '/api/market-indices': [{ code: '000001', name: '上证指数', price: 3800, changePct: 1.25, quoteValid: true, asOf: '2026-09-30T15:00:00', qualityStatus: 'FRESH_PRIMARY' }],
    '/api/watchlist': [
      { id: 1, code: '600001', name: '甲公司', type: 'STOCK', quoteValid: true, price: 12, changePct: -2, turnover: 2e8, quoteDate: '2026-09-30', attributionSummary: '订单变化', attributionReportId: 42, attributionReportDate: '2026-09-29' },
      { id: 2, code: '600002', name: '乙公司', type: 'STOCK', quoteValid: true, price: 20, changePct: 3, quoteDate: '2026-09-30' },
      { id: 3, code: '000003', name: '基金', type: 'FUND', quoteValid: true }
    ],
    '/api/sector-market/follows': [{ id: 1, code: '881001', name: '关注行业', changePct: 2.5, quoteValid: true, quoteDate: '2026-09-30' }],
    '/api/sector-market/movements?category=INDUSTRY&limit=5': {
      category: 'INDUSTRY', qualityStatus: 'FRESH_PRIMARY', retrievedAt: '2026-10-01T10:00:00',
      leaders: [{ code: '881001', name: '半导体', changePct: 3.5, quoteTime: '2026-09-30T15:00:00' }],
      laggards: [{ code: '881002', name: '银行', changePct: -1.5 }]
    },
    '/api/sector-market/movements?category=CONCEPT&limit=5': { category: 'CONCEPT', qualityStatus: 'FRESH_PRIMARY', leaders: [], laggards: [], warning: '概念涨跌幅暂不可用' },
    '/api/market-pulse/latest': {
      qualityStatus: 'READY', businessDate: '2026-09-30',
      breadth: { businessDate: '2026-09-30', advanceCount: 3000, declineCount: 2000, flatCount: 100, totalAmount: 1.5e12, changeSummary: { totalAmountChangeRatio: .12 } },
      sectors: [{ sectorCode: '881001', sectorName: '半导体', return5d: 6.25 }]
    }
  };
  vi.mocked(api).mockImplementation((path) => Promise.resolve(responses[path.replace('&refresh=true', '').replace('?refresh=true', '')]) as never);
});

async function renderOverview() {
  const onChangeView = vi.fn();
  const result = render(<DashboardMarketOverview refreshRevision={0} onChangeView={onChangeView} />);
  await screen.findByText('甲公司');
  await waitFor(() => expect(screen.getByRole('button', { name: '刷新行情' })).toBeEnabled());
  return { ...result, onChangeView };
}

test('shows market breadth, dated quotes and only stocks while never starting attribution', async () => {
  await renderOverview();
  expect(screen.getByText('3000')).toBeVisible();
  expect(screen.getByText(/较上一交易日 \+12.00%/)).toBeVisible();
  expect(screen.getByText(/2026-09-30 15:00 · 最近交易快照/)).toBeVisible();
  expect(screen.getByText('+6.25%')).toBeVisible();
  expect(screen.queryByText('基金')).not.toBeInTheDocument();
  expect(vi.mocked(api).mock.calls.some(([path, options]) => path.includes('/attribution/start') || options?.method === 'POST')).toBe(false);
});

test('sorts stocks, restores watchlist order and opens existing stock and attribution details', async () => {
  await renderOverview();
  const stockSection = screen.getByRole('heading', { name: '自选股速览' }).closest('section')!;
  await userEvent.click(screen.getByRole('button', { name: '按涨跌幅排序' }));
  expect(within(stockSection).getAllByRole('row')[1]).toHaveTextContent('乙公司');
  await userEvent.click(screen.getByRole('button', { name: '恢复自选顺序' }));
  expect(within(stockSection).getAllByRole('row')[1]).toHaveTextContent('甲公司');
  await userEvent.click(screen.getByRole('button', { name: /甲公司/ }));
  expect(screen.getByRole('dialog')).toHaveTextContent('行情 600001');
  await userEvent.click(screen.getByRole('button', { name: '关闭行情' }));
  await userEvent.click(screen.getByRole('button', { name: /订单变化.*2026-09-29/ }));
  expect(screen.getByText('归因报告 42')).toBeVisible();
});

test('switches category independently and opens the corresponding workspaces', async () => {
  const { onChangeView } = await renderOverview();
  await userEvent.click(screen.getByRole('button', { name: '概念' }));
  expect(await screen.findByText('概念涨跌幅暂不可用')).toBeVisible();
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === '/api/watchlist')).toHaveLength(1);
  await userEvent.click(screen.getByRole('button', { name: '查看市场状态' }));
  expect(onChangeView).toHaveBeenLastCalledWith('marketPulse');
  await userEvent.click(screen.getByRole('button', { name: '全部自选（2）' }));
  expect(onChangeView).toHaveBeenLastCalledWith('watchlist');
});

test('hides invalid quotes and does not combine sector returns from different trading dates', async () => {
  responses['/api/watchlist'] = [{ id: 1, code: '600001', name: '无效公司', type: 'STOCK', quoteValid: false, price: 999, changePct: 88, turnover: 1e10 }];
  responses['/api/market-pulse/latest'] = { qualityStatus: 'READY', businessDate: '2026-09-29', sectors: [{ sectorCode: '881001', return5d: 6.25 }] };
  render(<DashboardMarketOverview refreshRevision={0} onChangeView={vi.fn()} />);
  const row = (await screen.findByText('无效公司')).closest('tr')!;
  expect(row).not.toHaveTextContent('999');
  expect(row).not.toHaveTextContent('+88.00%');
  await waitFor(() => expect(screen.getByRole('button', { name: '刷新行情' })).toBeEnabled());
  expect(screen.queryByText('+6.25%')).not.toBeInTheDocument();
});

test('retains quotes and breadth after partial refresh failure and exposes retry feedback', async () => {
  await renderOverview();
  const original = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation((path, options) => path === '/api/market-pulse/latest' || path.startsWith('/api/market-indices') ? Promise.reject(new Error('offline')) : original(path, options));
  await userEvent.click(screen.getByRole('button', { name: '刷新行情' }));
  expect(await screen.findByText(/市场宽度刷新失败/)).toBeVisible();
  expect(screen.getByText('3800.00')).toBeVisible();
  expect(screen.getByText('3000')).toBeVisible();
  expect(await screen.findByText(/指数加载失败/)).toBeVisible();
});

test('topbar refresh revision refreshes quotes and a missing market resource leaves others available', async () => {
  responses['/api/market-pulse/latest'] = { qualityStatus: 'UNAVAILABLE', breadth: { qualityStatus: 'UNAVAILABLE', advanceCount: 9999 } };
  const { rerender } = await renderOverview();
  expect(screen.queryByText('9999')).not.toBeInTheDocument();
  expect(screen.getByText('3800.00')).toBeVisible();
  rerender(<DashboardMarketOverview refreshRevision={1} onChangeView={vi.fn()} />);
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/watchlist?refresh=true'));
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === '/api/market-pulse/latest')).toHaveLength(2);
});
