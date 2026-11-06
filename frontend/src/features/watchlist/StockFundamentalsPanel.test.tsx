import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { StockFundamentalsPanel } from './StockFundamentalsPanel';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const view = {
  instrument: { id: 42, code: '600519', type: 'STOCK', market: 'SH', name: '贵州茅台' },
  quarters: [
    { periodEnd: '2026-03-31', reportId: 1, currency: 'CNY', sourceCode: 'SOURCE', qualityStatus: 'FRESH', publishedAt: '2026-04-25', revenue: '100000000', netProfitParent: '-20000000', operatingCashFlow: '30000000', grossMarginPct: '42.5', dataGaps: [] },
    { periodEnd: '2026-06-30', currency: 'CNY', dataGaps: ['缺少前序累计报告'] },
    { periodEnd: '2026-09-30', reportId: 3, currency: 'CNY', sourceCode: 'SOURCE', qualityStatus: 'FRESH', revenue: '200000000', netProfitParent: '40000000', dataGaps: ['现金流数据缺失'] }
  ],
  valuation: {
    instrument: { id: 42, code: '600519', type: 'STOCK', name: '贵州茅台' },
    latest: { instrumentId: 42, observedDate: '2026-11-05', observedAt: '2026-11-05T07:00:00Z', sourceCode: 'VALUATION', qualityStatus: 'FRESH_PRIMARY', peTtm: 15, pbMrq: 2 },
    history: [{ instrumentId: 42, observedDate: '2026-11-05', observedAt: '2026-11-05T07:00:00Z', sourceCode: 'VALUATION', qualityStatus: 'FRESH_PRIMARY', peTtm: 15, pbMrq: 2 }],
    metrics: [{ metricCode: 'PE_TTM', value: 15, sampleCount3y: 1, sampleCount5y: 1, historyStatus: 'ACCUMULATING' }], corporateActions: [], warnings: []
  }, warnings: ['历史估值仍在积累'], refreshedAt: '2026-11-06T07:00:00Z'
};

beforeEach(() => {
  vi.mocked(api).mockReset();
});

test('automatically reads quarterly trends and genuine valuation samples without a setup action', async () => {
  vi.mocked(api).mockResolvedValue(view as never);
  const { container } = render(<StockFundamentalsPanel code="600519" />);
  expect(api).toHaveBeenCalledWith('/api/watchlist/stocks/600519/fundamentals');
  expect(await screen.findByRole('img', { name: '营业收入历史趋势' })).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '归母净利润历史趋势' })).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '市盈率 PE · TTM历史趋势' })).toBeInTheDocument();
  expect(screen.getByText('真实本地快照 · 1 个观测日')).toBeInTheDocument();
  expect(screen.getByText(/历史估值仍在积累/)).toBeInTheDocument();
  const revenue = screen.getByRole('img', { name: '营业收入历史趋势' });
  expect(revenue.querySelector('path')?.getAttribute('d')?.match(/M/g)).toHaveLength(2);
  expect(revenue.querySelector('path')?.getAttribute('d')).not.toContain('L');
  expect(container.textContent).not.toContain('NaN');
});

test('keeps missing statements visible and does not compare amounts across currencies', async () => {
  vi.mocked(api).mockResolvedValue({ ...view, quarters: [view.quarters[0], { ...view.quarters[2], currency: 'USD' }] } as never);
  render(<StockFundamentalsPanel code="600519" />);
  expect(await screen.findByText('报告币种不同，暂不跨币种绘制金额趋势。')).toBeInTheDocument();
  expect(screen.queryByRole('img', { name: '营业收入历史趋势' })).not.toBeInTheDocument();
  expect(screen.getByText('现金流数据缺失')).toBeInTheDocument();
});

test('refresh preserves successful prior data when the upstream request fails', async () => {
  vi.mocked(api).mockResolvedValueOnce(view as never).mockRejectedValueOnce(new Error('服务暂不可用'));
  render(<StockFundamentalsPanel code="600519" />);
  await screen.findByRole('img', { name: '营业收入历史趋势' });
  await userEvent.click(screen.getByRole('button', { name: '刷新财务与估值' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('服务暂不可用');
  expect(screen.getByRole('img', { name: '营业收入历史趋势' })).toBeInTheDocument();
  expect(api).toHaveBeenLastCalledWith('/api/watchlist/stocks/600519/fundamentals?refresh=true');
});

test('shows same-basis local peer samples without claiming an industry-wide average', async () => {
  vi.mocked(api).mockResolvedValue({ ...view,
    peers: [{ code: '000858', name: '五粮液', observedDate: '2026-11-05', sourceCode: 'VALUATION', qualityStatus: 'FRESH_PRIMARY', peTtm: 12.5, pbMrq: null }],
    peerComparisonNote: '仅比较本地同日同源样本，不代表完整行业。'
  } as never);
  render(<StockFundamentalsPanel code="600519" />);
  expect(await screen.findByRole('rowheader', { name: '五粮液 000858' })).toBeInTheDocument();
  expect(screen.getByRole('rowheader', { name: '贵州茅台 · 当前标的' })).toBeInTheDocument();
  expect(screen.getByRole('cell', { name: '12.50' })).toBeInTheDocument();
  expect(screen.getByText('仅比较本地同日同源样本，不代表完整行业。')).toBeInTheDocument();
});

test('does not let an older stock request overwrite the current stock', async () => {
  let finishOld!: (value: unknown) => void;
  vi.mocked(api).mockImplementation((path) => path.includes('600519')
    ? new Promise((resolve) => { finishOld = resolve; }) as never
    : Promise.resolve({ ...view, warnings: ['当前股票688012'] }) as never);
  const { rerender } = render(<StockFundamentalsPanel code="600519" />);
  rerender(<StockFundamentalsPanel code="688012" />);
  expect(await screen.findByText('当前股票688012')).toBeInTheDocument();
  await act(async () => {
    finishOld(view);
  });
  expect(within(screen.getByRole('heading', { name: '单季度财务趋势' }).closest('section')!).getByRole('img', { name: '营业收入历史趋势' })).toBeInTheDocument();
  expect(screen.getByText('当前股票688012')).toBeInTheDocument();
});
