import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import type { WatchlistItem } from '../../shared/types';
import { WatchlistResearchScan } from './WatchlistResearchScan';
import type { ResearchScanRun, ResearchScanView, ResearchSnapshot } from './researchScanTypes';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
const stock = { id: 1, code: '605058', name: '澳弘电子', type: 'STOCK' } as WatchlistItem;
const path = '/api/watchlist/research-scan';

function snapshot(title = '现金流与利润不同步'): ResearchSnapshot {
  return {
    benchmarkName: '沪深300', marketDate: '2026-11-06', totalStocks: 1, marketCovered: 1, financialCovered: 1,
    findings: [{ key: title, kind: 'CASH_LAG', title, evidence: ['利润增长20%，现金转化0.25倍'], codes: [stock.code], newlyObserved: true }],
    strengths: [{ code: stock.code, name: stock.name!, actualReturnPct: 10, baselineReturnPct: 3, excessReturnPp: 7,
      beta: 1.2, fitQuality: .64, positiveResidualDays: 15, trainingSamples: 120, evaluationSamples: 20,
      evaluatedFrom: '2026-10-01', evaluatedThrough: '2026-11-06', gaps: [] }],
    relationships: [{ leftCode: stock.code, leftName: stock.name!, rightCode: '603986', rightName: '兆易创新',
      correlation: .87, residualCorrelation: .65, downsideCorrelation: null, downsideSamples: 10, samples: 60, strong: true }],
    growthQuality: [{ code: stock.code, name: stock.name!, revenueGrowthPct: 20, profitGrowthPct: null,
      cashConversion: .25, grossMarginPct: 30, grossMarginChangePp: -3, periodFrom: '2025-10-01', periodThrough: '2026-09-30', gaps: [] }], gaps: []
  };
}

function run(id = 'latest', title?: string): ResearchScanRun {
  return { id, scanDate: '2026-11-06', status: 'READY', stage: 'COMPLETED', processedStocks: 1,
    totalStocks: 1, snapshot: snapshot(title) };
}

function view(): ResearchScanView {
  return { latest: run(), history: [{ ...run('a'), snapshot: null }, { ...run('b'), snapshot: null }] };
}

beforeEach(() => {
  vi.mocked(api).mockReset();
});
afterEach(() => {
  vi.useRealTimers();
});

test('automatically scans stocks and opens the existing stock detail with numerical evidence', async () => {
  const open = vi.fn();
  vi.mocked(api).mockResolvedValue(view());
  render(<WatchlistResearchScan items={[stock]} ready onOpen={open} />);
  expect(await screen.findByText('现金流与利润不同步')).toBeInTheDocument();
  expect(screen.getByText('利润增长20%，现金转化0.25倍')).toBeInTheDocument();
  expect(vi.mocked(api).mock.calls[0][1]?.method).toBe('GET');
  await userEvent.click(screen.getByRole('button', { name: /澳弘电子/ }));
  expect(open).toHaveBeenCalledWith(stock);
  await userEvent.click(screen.getByRole('tab', { name: '强弱分解' }));
  expect(screen.getByText('+7.00 pp')).toBeInTheDocument();
  expect(screen.getByText('1.20 / 64.00%')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('tab', { name: '增长质量' }));
  const row = screen.getByRole('row', { name: /澳弘电子/ });
  expect(within(row).getByText('—')).toBeInTheDocument();
  expect(within(row).getByText('0.25 倍')).toBeInTheDocument();
});

test('fund-only and not-ready watchlists do not request a research scan', async () => {
  const { rerender } = render(<WatchlistResearchScan items={[{ ...stock, type: 'FUND' }]} ready onOpen={vi.fn()} />);
  expect(screen.queryByRole('region', { name: '自选研究扫描' })).not.toBeInTheDocument();
  rerender(<WatchlistResearchScan items={[stock]} ready={false} onOpen={vi.fn()} />);
  expect(api).not.toHaveBeenCalled();
});

test('polls only while running and cancels polling after unmount', async () => {
  vi.useFakeTimers();
  const active = { ...run('active'), status: 'RUNNING' as const, stage: 'FINANCIAL' as const, snapshot: null };
  vi.mocked(api).mockResolvedValue({ ...view(), active });
  const { unmount } = render(<WatchlistResearchScan items={[stock]} ready onOpen={vi.fn()} />);
  await act(async () => {});
  expect(screen.getByText(/阶段 2\/2/)).toBeInTheDocument();
  expect(screen.getByText('现金流与利润不同步')).toBeInTheDocument();
  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(api).toHaveBeenCalledTimes(2);
  vi.mocked(api).mockResolvedValue(view());
  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(api).toHaveBeenCalledTimes(3);
  await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
  expect(api).toHaveBeenCalledTimes(3);
  unmount();
  await act(async () => { await vi.advanceTimersByTimeAsync(4000); });
  expect(api).toHaveBeenCalledTimes(3);
});

test('ignores an older history response after switching records', async () => {
  let resolveA!: (value: ResearchScanRun) => void;
  vi.mocked(api).mockImplementation((url) => {
    if (url === `${path}/a`) {
      return new Promise<ResearchScanRun>((resolve) => { resolveA = resolve; }) as never;
    }
    return Promise.resolve(url === `${path}/b` ? run('b', '历史B') : view()) as never;
  });
  render(<WatchlistResearchScan items={[stock]} ready onOpen={vi.fn()} />);
  await screen.findByText('现金流与利润不同步');
  fireEvent.change(screen.getByLabelText('选择扫描记录'), { target: { value: 'a' } });
  fireEvent.change(screen.getByLabelText('选择扫描记录'), { target: { value: 'b' } });
  expect(await screen.findByText('历史B')).toBeInTheDocument();
  await act(async () => { resolveA(run('a', '历史A')); });
  expect(screen.queryByText('历史A')).not.toBeInTheDocument();
  expect(screen.getByText('历史B')).toBeInTheDocument();
});

test('manual scan uses POST and membership changes restart with GET without leaking old results', async () => {
  vi.mocked(api).mockResolvedValue(view());
  const { rerender } = render(<WatchlistResearchScan items={[stock]} ready onOpen={vi.fn()} />);
  await screen.findByText('现金流与利润不同步');
  await userEvent.click(screen.getByRole('button', { name: '重新扫描' }));
  await waitFor(() => expect(vi.mocked(api).mock.calls[vi.mocked(api).mock.calls.length - 1]?.[1]?.method).toBe('POST'));
  vi.mocked(api).mockResolvedValue({ history: [] });
  rerender(<WatchlistResearchScan items={[{ ...stock, code: '603986' }]} ready onOpen={vi.fn()} />);
  await waitFor(() => expect(vi.mocked(api).mock.calls[vi.mocked(api).mock.calls.length - 1]?.[1]?.method).toBe('GET'));
  expect(screen.queryByText('现金流与利润不同步')).not.toBeInTheDocument();
});

test('handles unavailable and malformed responses without inventing numerical data', async () => {
  vi.mocked(api).mockRejectedValue(new Error('数据服务离线'));
  render(<WatchlistResearchScan items={[stock]} ready onOpen={vi.fn()} />);
  expect(await screen.findByText(/数据服务离线/)).toBeInTheDocument();
  vi.mocked(api).mockResolvedValue([]);
  await userEvent.click(screen.getByRole('button', { name: '重试读取' }));
  expect(await screen.findByText(/研究扫描响应格式不正确/)).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});
