import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { MarketPanorama } from './MarketPanorama';
import type { MarketPulseWorkspace } from './marketPulseTypes';
import type { PanoramaFrame } from './panoramaModel';
const sectors = Array.from({ length: 35 }, (_, i) => ({ sectorCode: `S${i}`, sectorName: `行业${i}`, rotationScore: i, return1d: i / 10, return5d: i / 5, breadthRatio: .6, rotationTrail: [{ businessDate: '2026-09-29', relativeStrength: i / 10, relativeMomentum: i / 20 }] }));
const frames: PanoramaFrame[] = ['2026-09-28', '2026-09-29'].map((date, i) => ({ businessDate: date, sectors, indices: [{ code: 'A', name: '指数', close: 100 + i }], advanceRatio: .5 + i / 10 }));
const workspace: MarketPulseWorkspace = { businessDate: '2026-09-29', qualityStatus: 'PARTIAL', sectors, breadth: { indices: frames[1].indices, advanceRatio: .6 } };
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
