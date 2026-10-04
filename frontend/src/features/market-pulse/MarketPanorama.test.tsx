import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { MarketPanorama } from './MarketPanorama';
import type { MarketEventConfirmation, MarketPulseWorkspace } from './marketPulseTypes';
import type { PanoramaFrame } from './panoramaModel';
const sectors = Array.from({ length: 35 }, (_, i) => ({ sectorCode: `S${i}`, sectorName: `行业${i}`, rotationScore: i, return1d: i / 10, return5d: i / 5, breadthRatio: .6, rotationTrail: [{ businessDate: '2026-09-29', relativeStrength: i / 10, relativeMomentum: i / 20 }] }));
const frames: PanoramaFrame[] = ['2026-09-28', '2026-09-29'].map((date, i) => ({ businessDate: date, sectors, indices: [{ code: 'A', name: '指数', close: 100 + i }], advanceRatio: .5 + i / 10 }));
const workspace: MarketPulseWorkspace = { businessDate: '2026-09-29', qualityStatus: 'PARTIAL', sectors, breadth: { indices: frames[1].indices, advanceRatio: .6 } };
const catalyst = (title: string, sectorCode = 'S0'): MarketEventConfirmation => ({ title, sectorCode, sectorName: `行业${sectorCode.slice(1)}`, eventScore: 80, marketReactionScore: 70, confirmationState: 'CONFIRMED' });
const response = (data: unknown) => ({ ok: true, status: 200, text: async () => JSON.stringify({ success: true, code: 'OK', message: '', traceId: '', timestamp: '', data }) }) as Response;
beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (input: string) => {
    if (input.includes('/panorama?')) {
      return response(frames);
    }
    if (input.includes('/focuses')) {
      return response([{ code: '600000', type: 'STOCK' }]);
    }
    return response({ businessDate: input.split('/').pop(), stocks: [{ instrumentCode: '600000.SH', instrumentName: '样本公司', return1d: 1, sectorCodes: ['S0'], groupCodes: [] }] });
  }));
});
function mount() {
  return render(<MarketPanorama workspace={workspace} dates={['2026-09-29']} refreshing={false} onLoad={vi.fn()} onRefresh={vi.fn()} onOpenStock={vi.fn()} />);
}
test('shows every industry and links matrix selection to date, map and rotation', async () => {
  mount();
  expect(await screen.findByRole('button', { name: '行业0 2026-09-28 当日涨跌 0.00%' })).toBeInTheDocument();
  expect(screen.getAllByRole('button', { name: /^选择行业/ })).toHaveLength(35);
  fireEvent.click(screen.getByRole('button', { name: '行业0 2026-09-28 当日涨跌 0.00%' }));
  expect(screen.getByRole('slider', { name: '全景观察日期' })).toHaveAttribute('aria-valuetext', '2026-09-28');
  expect(screen.getByRole('button', { name: '选择行业行业0' })).toHaveAttribute('aria-pressed', 'true');
  expect(within(screen.getByRole('complementary', { name: '选中行业详情' })).getByRole('heading', { name: '行业0' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '后一个观测日' }));
  expect(screen.getByRole('button', { name: '轮动选择行业0' })).toHaveAttribute('aria-pressed', 'true');
  fireEvent.click(screen.getByRole('button', { name: /展开行业个股/ }));
  expect(await screen.findByRole('button', { name: /样本公司/ })).toHaveTextContent('★');
  expect(vi.mocked(fetch).mock.calls.some(call => call[1]?.method === 'POST')).toBe(false);
});
test('reports failed history and can recover without a full page reload', async () => {
  const original = vi.mocked(fetch).getMockImplementation()!;
  vi.mocked(fetch).mockImplementation(async (...args) => {
    if (String(args[0]).includes('/panorama?')) {
      throw new Error('offline');
    }
    return original(...args);
  });
  mount();
  expect(await screen.findByText(/历史截面加载失败/)).toBeInTheDocument();
  vi.mocked(fetch).mockImplementation(original);
  fireEvent.click(screen.getByRole('button', { name: '重试' }));
  await waitFor(() => expect(screen.queryByText(/历史截面加载失败/)).not.toBeInTheDocument());
  expect(screen.getByRole('button', { name: '行业0 2026-09-28 当日涨跌 0.00%' })).toBeInTheDocument();
});

test('ignores a late stock response after moving the shared timeline', async () => {
  const original = vi.mocked(fetch).getMockImplementation()!;
  let completeOld: ((value: Response) => void) | undefined;
  vi.mocked(fetch).mockImplementation(async (...args) => {
    const url = String(args[0]);
    if (url.endsWith('/research/2026-09-29')) {
      return new Promise<Response>(resolve => { completeOld = resolve; });
    }
    if (url.endsWith('/research/2026-09-28')) {
      return response({ businessDate: '2026-09-28', stocks: [{ instrumentCode: '600000.SH', instrumentName: '28日公司', sectorCodes: ['S0'], return1d: 1, groupCodes: [] }] });
    }
    return original(...args);
  });
  mount();
  await waitFor(() => expect(completeOld).toBeDefined());
  fireEvent.click(screen.getByRole('button', { name: '行业0 2026-09-28 当日涨跌 0.00%' }));
  fireEvent.click(screen.getByRole('button', { name: /展开行业个股/ }));
  expect(await screen.findByRole('button', { name: /28日公司/ })).toBeInTheDocument();
  completeOld!(response({ businessDate: '2026-09-29', stocks: [{ instrumentCode: '600001.SH', instrumentName: '29日旧响应', sectorCodes: ['S0'], groupCodes: [] }] }));
  await waitFor(() => expect(screen.queryByRole('button', { name: /29日旧响应/ })).not.toBeInTheDocument());
  expect(screen.getByRole('button', { name: /28日公司/ })).toBeInTheDocument();
});

test('links catalyst industry selection, inspector, rotation and research context, and can clear the selection', async () => {
  const onOpenStockDiscovery = vi.fn();
  render(<MarketPanorama workspace={{ ...workspace, eventConfirmations: [catalyst('行业0催化'), catalyst('行业1催化', 'S1')],
    sectors: sectors.map((sector, i) => i ? sector : { ...sector, stage: 'PERSISTENT', persistenceDays: 4, crowdingScore: 58, mainNetInflow: 3200000000 }) }}
    dates={[]} refreshing={false} onLoad={vi.fn()} onRefresh={vi.fn()} onOpenStockDiscovery={onOpenStockDiscovery} />);
  fireEvent.change(screen.getByRole('textbox', { name: '查找行业' }), { target: { value: '行业2' } });
  fireEvent.click(screen.getByRole('button', { name: '联动行业行业0' }));
  expect(screen.getByRole('textbox', { name: '查找行业' })).toHaveValue('');
  const inspector = screen.getByRole('complementary', { name: '选中行业详情' });
  expect(within(inspector).getByText('持续')).toBeInTheDocument();
  expect(within(inspector).getByText('4 天')).toBeInTheDocument();
  expect(within(inspector).getByText('58')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '轮动选择行业0' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('button', { name: '选择行业行业0' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.queryByText('行业1催化')).not.toBeInTheDocument();
  fireEvent.click(within(inspector).getByText('资金与更多指标'));
  expect(within(inspector).getByText('+32.0 亿')).toBeVisible();
  fireEvent.click(within(inspector).getByRole('button', { name: '进入行业研究 →' }));
  expect(onOpenStockDiscovery).toHaveBeenCalledWith(expect.objectContaining({ businessDate: '2026-09-29', preferredSectors: ['行业0'] }));
  fireEvent.click(screen.getByRole('button', { name: '清除选择' }));
  expect(screen.getByRole('heading', { name: '强弱分布' })).toBeInTheDocument();
  expect(screen.getByText('行业1催化')).toBeInTheDocument();
});

test('loads the selected historical catalyst snapshot and ignores a late response after returning to the end date', async () => {
  const original = vi.mocked(fetch).getMockImplementation()!;
  let completeOld: ((value: Response) => void) | undefined;
  vi.mocked(fetch).mockImplementation(async (...args) => {
    if (String(args[0]) === '/api/market-pulse/2026-09-28') {
      return new Promise<Response>(resolve => { completeOld = resolve; });
    }
    return original(...args);
  });
  render(<MarketPanorama workspace={{ ...workspace, eventConfirmations: [catalyst('29日催化')] }} dates={[]} refreshing={false} onLoad={vi.fn()} onRefresh={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: '行业0 2026-09-28 当日涨跌 0.00%' }));
  expect(screen.queryByText('29日催化')).not.toBeInTheDocument();
  await waitFor(() => expect(completeOld).toBeDefined());
  fireEvent.click(screen.getByRole('button', { name: '后一个观测日' }));
  completeOld!(response({ businessDate: '2026-09-28', eventConfirmations: [catalyst('过期响应')] }));
  await waitFor(() => expect(screen.getByText('29日催化')).toBeInTheDocument());
  expect(screen.queryByText('过期响应')).not.toBeInTheDocument();
});

test('retries mismatched catalyst dates without changing the market view', async () => {
  const original = vi.mocked(fetch).getMockImplementation()!;
  let matching = false;
  vi.mocked(fetch).mockImplementation(async (...args) => {
    if (String(args[0]) === '/api/market-pulse/2026-09-28') {
      return response({ businessDate: matching ? '2026-09-28' : '2026-09-29', eventConfirmations: [catalyst('28日催化')] });
    }
    return original(...args);
  });
  mount();
  fireEvent.click(await screen.findByRole('button', { name: '行业0 2026-09-28 当日涨跌 0.00%' }));
  expect(await screen.findByRole('button', { name: '重试行业催化' })).toBeInTheDocument();
  expect(screen.queryByText('28日催化')).not.toBeInTheDocument();
  matching = true;
  fireEvent.click(screen.getByRole('button', { name: '重试行业催化' }));
  expect(await screen.findByText('28日催化')).toBeInTheDocument();
  expect(screen.getByRole('slider', { name: '全景观察日期' })).toHaveAttribute('aria-valuetext', '2026-09-28');
});
