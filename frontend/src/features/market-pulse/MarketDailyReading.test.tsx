import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { api } from '../../shared/api/client';
import { MarketDailyReading } from './MarketDailyReading';
import type { MarketReading } from './MarketDailyReading';
import type { MarketPulseWorkspace } from './marketPulseTypes';
vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));
const workspace: MarketPulseWorkspace = { businessDate: '2026-11-17', qualityStatus: 'READY',
  dailyReview: { headline: '规则判断：结构分化' }, breadth: { advanceRatio: .63, medianChangePct: .7, totalAmount: 2.3e12 } };
const point = (title: string) => ({ title, detail: '指数表现需要与参与度一同核对。', factIds: ['F1'] });
const reading: MarketReading = {
  status: 'READY', businessDate: '2026-11-17', asOf: '2026-11-17T15:30:00', sectorNames: { tech: '电子' },
  facts: [{ id: 'F1', label: '市场参与', value: '上涨比例 63%', businessDate: '2026-11-17' }],
  report: { headline: '修复扩散，量能仍需跟进', summary: '市场参与范围扩大，但还不能定义趋势已经确认。', stateLabel: '结构修复',
    change: point('参与度较前期改善'), drivers: [point('上涨范围扩大')], contradictions: [point('成交尚待跟进')],
    conditions: [point('观察参与度是否保持')], sectors: [{ sectorCode: 'tech', reason: '行业内部参与改善。', watchCondition: '观察内部宽度能否维持。', factIds: ['F1'] }] }
};
const props = { workspace, dates: ['2026-11-17'], onLoad: vi.fn(), onOpenPanorama: vi.fn(), onOpenBreadth: vi.fn() };
beforeEach(() => { vi.mocked(api).mockReset(); vi.clearAllMocks(); });
test('reads a concise thesis and facts without generating again', async () => {
  vi.mocked(api).mockResolvedValue(reading);
  render(<MarketDailyReading {...props} />);
  expect(await screen.findByRole('heading', { name: reading.report!.headline })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '参与度较前期改善' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '电子' })).toBeInTheDocument();
  fireEvent.click(screen.getAllByText(/核对依据/)[0]);
  expect(screen.getAllByText('上涨比例 63%').length).toBeGreaterThan(0);
  fireEvent.click(screen.getByRole('button', { name: /查看市场宽度/ }));
  expect(props.onOpenBreadth).toHaveBeenCalledOnce();
  expect(vi.mocked(api).mock.calls.every(([, options]) => options?.method !== 'POST')).toBe(true);
});
test('generates missing review only on explicit request', async () => {
  vi.mocked(api).mockResolvedValue({ ...reading, report: undefined, status: 'NOT_GENERATED' });
  render(<MarketDailyReading {...props} />);
  fireEvent.click(await screen.findByRole('button', { name: '生成当日研判' }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/market-pulse/ai/2026-11-17?retry=false', { method: 'POST' }));
});
test('unavailable endpoint preserves rule overview and navigation', async () => {
  vi.mocked(api).mockRejectedValue(new Error('HTTP 404'));
  render(<MarketDailyReading {...props} />);
  expect(await screen.findByRole('button', { name: '重新读取研判' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '规则判断：结构分化' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /核对市场全景/ })).toBeEnabled();
});
test('does not show a different date response as today', async () => {
  vi.mocked(api).mockResolvedValue({ ...reading, businessDate: '2026-11-16' });
  render(<MarketDailyReading {...props} />);
  await screen.findByRole('button', { name: '重新读取研判' });
  expect(screen.queryByRole('heading', { name: reading.report!.headline })).not.toBeInTheDocument();
});
