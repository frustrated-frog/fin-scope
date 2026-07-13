import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { WatchlistView } from './WatchlistView';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

const indexQuotes = [
  { code: '000001', name: '上证指数', price: 3200.12, changeAmount: 12.5, changePct: 0.39, quoteValid: true },
  { code: '399001', name: '深证成指', price: 10100.24, changeAmount: -52.8, changePct: -0.52, quoteValid: true },
  { code: '399006', name: '创业板指', price: 2020.35, changeAmount: 0, changePct: 0, quoteValid: true },
  { code: '000688', name: '科创50', quoteValid: false, quoteNote: '行情抓取失败' }
];

beforeEach(() => {
  vi.mocked(api).mockReset();
});

test('renders market index cards above the watchlist controls', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/market-indices' ? indexQuotes : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '市场指数' })).toBeInTheDocument();
  expect(screen.getAllByTestId('market-index-card')).toHaveLength(4);
  expect(screen.getByText('3200.12')).toHaveClass('watchlist-up');
  expect(screen.getByText('+12.50')).toHaveClass('watchlist-up');
  expect(screen.getByText('+0.39%')).toHaveClass('watchlist-up');
  expect(screen.getByText('10100.24')).toHaveClass('watchlist-down');
  expect(screen.getByText('-52.80')).toHaveClass('watchlist-down');
  expect(screen.getByText('-0.52%')).toHaveClass('watchlist-down');
  expect(screen.getByText('行情抓取失败')).toBeInTheDocument();
});

test('places market indices outside the watchlist panel and shows directional change badges', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/market-indices' ? indexQuotes : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  const indexSection = (await screen.findByRole('heading', { name: '市场指数' })).closest('.market-index-overview');
  expect(indexSection).not.toBeNull();
  expect(indexSection?.nextElementSibling).toHaveClass('panel', 'wide');
  expect(screen.getByText('+0.39%')).toHaveClass('watchlist-up');
  expect(screen.getByText('-0.52%')).toHaveClass('watchlist-down');
  expect(screen.queryByText(/↑|↓/)).not.toBeInTheDocument();
});

test('refreshes market indices and watchlist together', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockResolvedValue([] as never);
  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  await screen.findByRole('button', { name: '刷新行情' });
  vi.mocked(api).mockClear();

  await user.click(screen.getByRole('button', { name: '刷新行情' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/market-indices?refresh=true'));
  expect(api).toHaveBeenCalledWith('/api/watchlist?refresh=true');
});

test('shows a perceptible refresh state and reports unchanged fresh data', async () => {
  const user = userEvent.setup();
  const item = { id: 1, code: '600519', type: 'STOCK', name: '贵州茅台', quoteValid: true, price: 1500, changePct: 1.2 };
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(path === '/api/watchlist' ? [item] : []) as never);
  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);
  await screen.findByText('贵州茅台');

  let resolveWatchlist: (value: unknown) => void = () => undefined;
  let resolveIndices: (value: unknown) => void = () => undefined;
  vi.mocked(api).mockImplementation((path: string) => new Promise((resolve) => {
    if (path.startsWith('/api/watchlist')) resolveWatchlist = resolve;
    if (path.startsWith('/api/market-indices')) resolveIndices = resolve;
  }) as never);

  await user.click(screen.getByRole('button', { name: '刷新行情' }));

  expect(screen.getByRole('button', { name: /刷新中/ })).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('正在从行情源获取最新数据');
  expect(api).toHaveBeenCalledWith('/api/watchlist?refresh=true');
  expect(api).toHaveBeenCalledWith('/api/market-indices?refresh=true');

  resolveWatchlist([item]);
  resolveIndices([]);

  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('已刷新，行情暂无变化'));
  expect(screen.getByRole('button', { name: '刷新行情' })).toBeEnabled();
});

test('does not present a failed watchlist request as an empty watchlist', async () => {
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/watchlist') {
      return Promise.reject(new Error('Internal server error'));
    }
    return Promise.resolve(indexQuotes) as never;
  });

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('alert')).toHaveTextContent('自选列表加载失败');
  expect(screen.getByText('加载失败')).toBeInTheDocument();
  expect(screen.queryByText('0 标的')).not.toBeInTheDocument();
});

test('opens a persisted attribution report from the clickable card summary', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 1, code: '600519', type: 'STOCK', name: '贵州茅台', quoteValid: true,
      quoteDate: '2026-07-13', attributionReportId: 88, attributionReportDate: '2026-07-13',
      attributionSummary: '白酒板块与业绩预期共同驱动'
    }] : path === '/api/market-indices' ? [] : {
      id: 88, instrumentCode: '600519', instrumentType: 'STOCK', status: 'COMPLETED',
      reportDate: '2026-07-13', summary: '白酒板块与业绩预期共同驱动', drivers: []
    }
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('今日归因')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /重新归因/ })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: /查看贵州茅台的完整归因报告/ }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/attribution/reports/88'));
  expect(screen.getByRole('button', { name: '← 返回自选' })).toBeInTheDocument();
});

test('uses deep attribution when the latest report belongs to an older quote date', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 1, code: '021894', type: 'FUND', name: '半导体基金', quoteValid: true,
      quoteDate: '2026-07-13', attributionReportId: 77, attributionReportDate: '2026-07-12',
      attributionSummary: '半导体板块回调'
    }] : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('最近归因')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /深度归因/ })).toBeInTheDocument();
});
