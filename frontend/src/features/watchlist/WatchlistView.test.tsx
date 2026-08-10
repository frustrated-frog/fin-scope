import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { WatchlistView } from './WatchlistView';
import { clearWatchlistDailyBarCache } from './watchlistDailyBarCache';

vi.mock('../../shared/api/client', () => ({
  api: vi.fn()
}));

const indexQuotes = [
  { code: '000001', name: '上证指数', price: 3200.12, changeAmount: 12.5, changePct: 0.39, quoteValid: true },
  { code: '399001', name: '深证成指', price: 10100.24, changeAmount: -52.8, changePct: -0.52, quoteValid: true },
  { code: '399006', name: '创业板指', price: 2020.35, changeAmount: 0, changePct: 0, quoteValid: true },
  { code: '000688', name: '科创50', quoteValid: false, quoteNote: '行情抓取失败' }
];

const industryOverview = {
  category: 'INDUSTRY', qualityStatus: 'FRESH_PRIMARY', retrievedAt: '2026-07-14T10:00:00',
  leaders: [{ code: 'BK1036', name: '半导体', category: 'INDUSTRY', price: 1234.5, changePct: 2.6, turnover: 12000000000, leaderStockName: '中芯国际' }],
  laggards: [{ code: 'BK0420', name: '旅游酒店', category: 'INDUSTRY', price: 876.5, changePct: -1.8, turnover: 3200000000 }]
};

beforeEach(() => {
  clearWatchlistDailyBarCache();
  vi.mocked(api).mockReset();
});

test('scopes the market-glass visual system to the watchlist page', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(
    /\.watchlist-page\s*{[^}]*--watchlist-cyan:\s*#4ddbc8;[^}]*--watchlist-blue:\s*#6f8cff;[^}]*isolation:\s*isolate;/s
  );
  expect(styles).toMatch(
    /\.watchlist-page\s+\.market-index-card\s*{[^}]*border-radius:\s*16px;[^}]*backdrop-filter:\s*blur\(14px\)\s+saturate\(135%\);/s
  );
  expect(styles).toMatch(
    /\.watchlist-page\s+\.watchlist-card\s*{[^}]*overflow:\s*hidden;[^}]*border-radius:\s*18px;[^}]*box-shadow:/s
  );
  expect(styles).toMatch(/\.watchlist-group\s+\.watchlist-grid\s*{[^}]*padding:\s*14px;/s);
  expect(styles).toMatch(/\.watchlist-page\s+\.watchlist-card::before\s*{/s);
  expect(styles).toMatch(/@media\s*\(prefers-reduced-transparency:\s*reduce\)[\s\S]*\.watchlist-page\s+\.watchlist-card/s);
  expect(styles).toMatch(/@media\s*\(prefers-contrast:\s*more\)[\s\S]*\.watchlist-page\s+\.watchlist-card/s);
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

test('opens the stock chart in a modal while keeping the watchlist visible', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 1, code: '600519', type: 'STOCK', name: '贵州茅台', quoteValid: true, price: 1500, changePct: 1.2
    }] : path === '/api/watchlist/600519/daily-bars?limit=120' ? [{
      code: '600519', tradeDate: '2026-08-05', open: 1500, high: 1520, low: 1490, close: 1510, volume: 50000
    }] : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.click(await screen.findByText('贵州茅台'));

  expect(await screen.findByRole('dialog', { name: '贵州茅台 行情图表' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '我的自选' })).toBeInTheDocument();
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
  expect(api).toHaveBeenCalledWith('/api/sector-market/overview?category=INDUSTRY&limit=5&refresh=true');
  expect(api).toHaveBeenCalledWith('/api/sector-market/follows?refresh=true');
});

test('shows independent Sina sector rankings without offering an invalid follow action', async () => {
  vi.mocked(api).mockImplementation((path: string) => {
    if (path.startsWith('/api/sector-market/overview')) return Promise.resolve({
      ...industryOverview,
      qualityStatus: 'FRESH_FALLBACK',
      leaders: [{ code: 'SINA:new_blhy', name: '玻璃行业', category: 'INDUSTRY', changePct: 3.2 }],
      laggards: []
    }) as never;
    return Promise.resolve([]) as never;
  });

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('玻璃行业')).toBeInTheDocument();
  const follow = screen.getByRole('button', { name: '关注-玻璃行业' });
  expect(follow).toBeDisabled();
  expect(follow).toHaveAttribute('title', '备用源板块暂不支持关注');
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
    if (path.startsWith('/api/sector-market/overview')) resolve({
      ...industryOverview, leaders: [], laggards: []
    });
    if (path.startsWith('/api/sector-market/follows')) resolve([]);
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

  expect(await screen.findByText(/自选列表加载失败：Internal server error/)).toBeInTheDocument();
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
  expect(screen.getByRole('button', { name: '返回自选' })).toBeInTheDocument();
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

test('uses the latest confirmed fund return when an intraday estimate is unavailable', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 1,
      code: '021894',
      type: 'FUND',
      name: '易方达半导体设备ETF联接C',
      quoteValid: true,
      confirmedNav: 2.6222,
      confirmedNavDate: '2026-07-21',
      confirmedNavChangePct: 14.6,
      quoteDate: '2026-07-21',
      quoteNote: '最新确认净值 2026-07-21；盘中估值暂不可用'
    }] : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('易方达半导体设备ETF联接C')).toBeInTheDocument();
  expect(screen.getByText('07-21')).toHaveAttribute('datetime', '2026-07-21');
  expect(screen.getByText('07-21')).toHaveAttribute('title', '确认净值日期 2026-07-21');
  expect(screen.getByText('均 +14.60%')).toHaveClass('watchlist-up');
  expect(screen.getByText('盘中估值')).toBeInTheDocument();
  expect(screen.getAllByText('--')).toHaveLength(1);
});

test('shows the intraday fund estimate in a compact secondary row', async () => {
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 1,
      code: '021894',
      type: 'FUND',
      name: '易方达半导体设备ETF联接C',
      quoteValid: true,
      confirmedNav: 2.6222,
      confirmedNavDate: '2026-07-21',
      confirmedNavChangePct: 14.6,
      price: 2.6322,
      changePct: 0.38,
      asOf: '2026-07-22T02:33:00',
      quoteTime: '2026-07-22T10:33:00',
      quoteDate: '2026-07-21'
    }] : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('易方达半导体设备ETF联接C')).toBeInTheDocument();
  expect(screen.getByText('2.6222')).toBeInTheDocument();
  expect(screen.getByText('+14.60%')).toBeInTheDocument();
  expect(screen.getByText('2.6322')).toBeInTheDocument();
  expect(screen.getByText('+0.38%')).toBeInTheDocument();
  expect(screen.getByText('10:33')).toBeInTheDocument();
});

test('places a separate sector market panel between indices and investment watchlist', async () => {
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/market-indices') return Promise.resolve(indexQuotes) as never;
    if (path.startsWith('/api/sector-market/overview')) return Promise.resolve(industryOverview) as never;
    return Promise.resolve([]) as never;
  });

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  const indexSection = (await screen.findByRole('heading', { name: '市场指数' })).closest('section');
  const sectorSection = screen.getByRole('heading', { name: '板块行情' }).closest('section');
  const investmentSection = screen.getByRole('heading', { name: '我的自选' }).closest('section');
  expect(indexSection?.nextElementSibling).toBe(sectorSection);
  expect(sectorSection?.nextElementSibling).toBe(investmentSection);
  expect(screen.getByText('半导体')).toBeInTheDocument();
  expect(screen.getByText('旅游酒店')).toBeInTheDocument();
  expect(screen.queryByRole('option', { name: '板块' })).not.toBeInTheDocument();
});

test('follows a ranked sector and shows it only in the sector workspace', async () => {
  const user = userEvent.setup();
  let followed = false;
  vi.mocked(api).mockImplementation((path: string, options?: RequestInit) => {
    if (path.startsWith('/api/sector-market/overview')) return Promise.resolve(industryOverview) as never;
    if (path === '/api/sector-market/follows/BK1036' && options?.method === 'PUT') {
      followed = true;
      return Promise.resolve({}) as never;
    }
    if (path === '/api/sector-market/follows') return Promise.resolve(followed ? [{
      id: 3, code: 'BK1036', name: '半导体', price: 1234.5, changePct: 2.6, quoteValid: true
    }] : []) as never;
    return Promise.resolve([]) as never;
  });

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '关注-半导体' }));

  expect(await screen.findByTestId('followed-sector-BK1036')).toBeInTheDocument();
  expect(screen.queryByText('BK1036 · 板块')).not.toBeInTheDocument();
});

test('switches ranking category and searches sectors outside the current ranking', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation((path: string) => {
    if (path.includes('category=CONCEPT') && path.startsWith('/api/sector-market/overview')) {
      return Promise.resolve({ ...industryOverview, category: 'CONCEPT', leaders: [], laggards: [] }) as never;
    }
    if (path.startsWith('/api/sector-market/overview')) return Promise.resolve(industryOverview) as never;
    if (path.startsWith('/api/sector-market/search')) return Promise.resolve({
      qualityStatus: 'FRESH_PRIMARY', items: [{ code: 'BK0987', name: '人形机器人', category: 'CONCEPT', changePct: 1.4 }]
    }) as never;
    return Promise.resolve([]) as never;
  });

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);
  await user.click(await screen.findByRole('button', { name: '概念板块' }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/sector-market/overview?category=CONCEPT&limit=5'));

  await user.type(screen.getByRole('textbox', { name: '搜索板块' }), '机器人');
  expect(await screen.findByRole('option', { name: /人形机器人/ })).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/sector-market/search?q=%E6%9C%BA%E5%99%A8%E4%BA%BA&category=ALL&limit=10');
});

test('clearly marks stale quotes and explains that they are not real-time data', async () => {
  const staleQuality = {
    qualityStatus: 'STALE_FALLBACK', sourceCode: 'LAST_GOOD_SNAPSHOT', staleAgeSeconds: 1620,
    warning: '实时行情源暂不可用，已回退到最近一次成功快照'
  };
  vi.mocked(api).mockImplementation((path: string) => {
    if (path === '/api/market-indices') return Promise.resolve([{
      ...indexQuotes[0], ...staleQuality
    }]) as never;
    if (path === '/api/watchlist') return Promise.resolve([{
      id: 1, code: '600519', type: 'STOCK', name: '贵州茅台', price: 1500,
      changePct: 1.2, quoteValid: true, ...staleQuality
    }]) as never;
    if (path.startsWith('/api/sector-market/overview')) return Promise.resolve({
      ...industryOverview, ...staleQuality
    }) as never;
    return Promise.resolve([]) as never;
  });

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  const alerts = await screen.findAllByRole('alert');
  expect(alerts[0]).toHaveTextContent('当前展示的是旧数据');
  expect(alerts[0]).toHaveTextContent('27 分钟前');
  expect(alerts[0]).toHaveTextContent('请勿视为实时行情');
  expect(screen.getAllByText('旧数据').length).toBeGreaterThanOrEqual(3);
});

test('opens the daily kline drawer when clicking a stock card', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 1, code: '600519', type: 'STOCK', name: '贵州茅台', quoteValid: true, price: 1500, changePct: 1.2
    }] : path === '/api/market-indices' ? [] : path === '/api/watchlist/600519/daily-bars?limit=120' ? [{
      tradeDate: '2026-07-31', open: 1510, high: 1530, low: 1505, close: 1528, volume: 60000
    }] : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('贵州茅台')).toBeInTheDocument();
  await user.click(screen.getByText('贵州茅台'));

  expect(await screen.findByRole('dialog', { name: '贵州茅台 行情图表' })).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/watchlist/600519/daily-bars?limit=120');
  expect(await screen.findByText('2026-07-31')).toBeInTheDocument();
});

test('opens disclosed holdings instead of a kline when clicking a fund card', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 2, code: '021894', type: 'FUND', name: '半导体基金', quoteValid: true,
      confirmedNav: 2.62, confirmedNavDate: '2026-08-08', confirmedNavChangePct: 1.2
    }] : path === '/api/watchlist/021894/fund-holdings?refresh=true' ? {
      fundCode: '021894', fundName: '半导体基金', disclosureDate: '2026-06-30',
      retrievedAt: '2026-08-10T14:30:00', topHoldingsWeightPct: 8,
      estimatedContributionPct: 0.16, estimatedHoldingCount: 1, totalHoldingCount: 1,
      lookThrough: false, note: '按最近披露持仓估算', holdings: [{
        rank: 1, stockCode: '688012', stockName: '中微公司', weightPct: 8,
        latestPrice: 468.5, changePct: 2, estimatedContributionPct: 0.16, quoteValid: true
      }]
    } : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  await user.click(await screen.findByText('半导体基金'));

  expect(await screen.findByRole('dialog', { name: '半导体基金 持仓透视' })).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/watchlist/021894/fund-holdings?refresh=true');
  expect(api).not.toHaveBeenCalledWith('/api/watchlist/021894/daily-bars?limit=120');
});

test('opens fund holdings from the keyboard-accessible card', async () => {
  const user = userEvent.setup();
  vi.mocked(api).mockImplementation((path: string) => Promise.resolve(
    path === '/api/watchlist' ? [{
      id: 2, code: '021894', type: 'FUND', name: '半导体基金', quoteValid: true,
      confirmedNav: 2.62
    }] : path === '/api/watchlist/021894/fund-holdings?refresh=true' ? {
      fundCode: '021894', fundName: '半导体基金', disclosureDate: '2026-06-30',
      retrievedAt: '2026-08-10T14:30:00', topHoldingsWeightPct: 0,
      estimatedHoldingCount: 0, totalHoldingCount: 0, lookThrough: false,
      note: '按最近披露持仓估算', holdings: []
    } : []
  ) as never);

  render(<WatchlistView addToast={vi.fn()} setMessage={vi.fn()} />);

  const card = (await screen.findByText('半导体基金')).closest('article');
  expect(card).toHaveAttribute('tabindex', '0');
  card?.focus();
  await user.keyboard('{Enter}');

  expect(await screen.findByRole('dialog', { name: '半导体基金 持仓透视' })).toBeInTheDocument();
});
