import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { CohortResearchPanel } from './CohortResearchPanel';
import type { DailyResearch } from './marketResearchTypes';

const research: DailyResearch = {
  businessDate: '2026-09-30', selectionDate: '2026-09-29', sourceCode: 'LOCAL_DAILY_BAR_PANEL', qualityStatus: 'PARTIAL', sampleCount: 6, warnings: [],
  stocks: [-4, -1, 0, 2, 3, null].map((return1d, index) => ({ instrumentCode: `60000${index}.SH`, instrumentName: `样本${index}`, return1d, groupCodes: ['STRONG'], sectorNames: ['半导体'], opportunityState: index === 0 ? 'WEAKENING' : 'CONTINUING', previousOpportunityState: 'CONTINUING' })),
  groups: [{ code: 'STRONG', label: '昨日强势组', definition: '前一交易日涨幅至少3%', eligibleCount: 6, memberCount: 6, validCount: 5, members: Array.from({ length: 6 }, (_, index) => `60000${index}.SH`) }],
};
function response(data: unknown) {
  return { ok: true, status: 200, text: async () => JSON.stringify({ success: true, code: 'SUCCESS', message: '', traceId: 'test', timestamp: '', data }) } as Response;
}
afterEach(() => vi.unstubAllGlobals());

test('filters yesterday members by actual outcome and opens the existing stock destination', () => {
  const open = vi.fn();
  render(<CohortResearchPanel research={research} onOpenStock={open} />);
  fireEvent.click(screen.getByRole('button', { name: /回落\s*1\s*只/ }));
  const members = screen.getByText('查看成员（1）').closest('details')!;
  expect(members).toHaveAttribute('open');
  expect(within(members).getByText('样本0 600000.SH')).toBeInTheDocument();
  expect(within(members).queryByText('样本1 600001.SH')).not.toBeInTheDocument();
  fireEvent.click(within(members).getByRole('button', { name: '产业链研究' }));
  expect(open).toHaveBeenCalledWith('600000');
  fireEvent.click(screen.getByRole('button', { name: /缺行情\s*1\s*只/ }));
  expect(within(members).getByText('样本5 600005.SH')).toBeInTheDocument();
});

test('selects an opportunity and can return to the full yesterday group', () => {
  render(<CohortResearchPanel research={research} />);
  fireEvent.click(screen.getByRole('button', { name: /强势转弱/ }));
  expect(screen.getByText('查看成员（1）')).toBeInTheDocument();
  expect(screen.getByText(/昨日满足趋势条件，今日不再满足。当前行业分布：半导体 1只/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /昨日强势组/ }));
  expect(screen.getByText('查看成员（6）')).toBeInTheDocument();
});

test('loads watchlist only on demand and recomputes the group for stock focuses', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([{ code: 'SH:600000', type: 'STOCK' }, { code: '600001', type: 'FUND' }])));
  render(<CohortResearchPanel research={research} />);
  expect(fetch).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '仅看自选' }));
  await screen.findByText('查看成员（1）');
  expect(screen.getByText(/有效样本少于5只/)).toBeInTheDocument();
  expect(screen.getByText('同一批 1 只股票，对比前一交易日')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '全部样本' }));
  expect(screen.getByText('查看成员（6）')).toBeInTheDocument();
});

test('watchlist failures are retryable and do not silently show all stocks as watched', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('暂不可用')).mockResolvedValue(response([])));
  render(<CohortResearchPanel research={research} />);
  fireEvent.click(screen.getByRole('button', { name: '仅看自选' }));
  fireEvent.click(await screen.findByRole('button', { name: '重试自选加载' }));
  await screen.findByText(/当前自选没有可用的日频样本/);
  await waitFor(() => expect(screen.queryByRole('button', { name: '重试自选加载' })).not.toBeInTheDocument());
});

test('shows a local empty state for an older server without hiding existing cohorts', () => {
  render(<CohortResearchPanel research={{ ...research, stocks: research.stocks.map(stock => ({ ...stock, opportunityState: undefined, previousOpportunityState: undefined })) }} />);
  expect(screen.getByText(/暂缺连续两日的走势状态/)).toBeInTheDocument();
  expect(screen.getByText('昨日强势组')).toBeInTheDocument();
});
