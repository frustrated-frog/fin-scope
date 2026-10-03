import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';
import { PersonalMarketPanel } from './PersonalMarketPanel';
import { industryChanges, opportunitySectors, relativeChanges } from './personalMarket';
const sectors = [
  { sectorCode: 'a', sectorName: '电网设备', return1d: 2, rotationScore: 80 },
  { sectorCode: 'b', sectorName: '半导体', return1d: 3, rotationScore: 90 },
];
const focus = {
  watchlistId: 1,
  code: '600001',
  type: 'STOCK' as const,
  name: '测试公司',
  direction: '电网设备',
  reason: '订单增长',
};
const event = {
  id: 'event:1',
  category: 'COMPANY',
  code: '600001',
  name: '测试公司',
  title: '海外新订单',
  occurredAt: '2026-09-30',
  sampleId: 7,
  reason: '订单增长',
};
function response(data: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () =>
      JSON.stringify({
        success: true,
        data,
        code: 'FS-0000',
        message: '',
        traceId: 't',
        timestamp: '',
      }),
  } as Response;
}
beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url) =>
      response(
        String(url).includes('/focuses')
          ? [focus]
          : String(url).includes('/personal?')
            ? [event]
            : { status: 'EMPTY' },
      ),
    ),
  );
});
test('shows related changes, filters and navigates existing event and company', async () => {
  const onEvent = vi.fn();
  const onWatch = vi.fn();
  const onDiscovery = vi.fn();
  render(
    <PersonalMarketPanel
      businessDate="2026-09-30"
      sectors={sectors}
      addToast={vi.fn()}
      onOpenEvent={onEvent}
      onOpenWatchlist={onWatch}
      onOpenStockDiscovery={onDiscovery}
    />,
  );
  await screen.findByText('海外新订单');
  fireEvent.click(screen.getByText('查看事件'));
  expect(onEvent).toHaveBeenCalledWith(
    expect.objectContaining({ sampleId: 7 }),
  );
  fireEvent.click(screen.getByText('查看公司'));
  expect(onWatch).toHaveBeenCalledWith('600001');
  fireEvent.click(screen.getByRole('button', { name: /行业变化/ }));
  expect(screen.queryByText('海外新订单')).not.toBeInTheDocument();
  expect(screen.getByText('电网设备当日上涨 2.00%')).toBeInTheDocument();
  fireEvent.click(screen.getByText('研究这个方向'));
  expect(onDiscovery).toHaveBeenCalledWith(
    expect.objectContaining({
      businessDate: '2026-09-30',
      preferredSectors: ['电网设备'],
    }),
  );
  expect(screen.getByText('其他市场方向')).toBeInTheDocument();
});
test('keeps market opportunities available when there is no watchlist', async () => {
  vi.mocked(fetch).mockImplementation(async (url) =>
    response(String(url).includes('latest') ? { status: 'EMPTY' } : []),
  );
  const open = vi.fn();
  render(
    <PersonalMarketPanel
      businessDate="2026-09-30"
      sectors={sectors}
      addToast={vi.fn()}
      onOpenWatchlist={open}
    />,
  );
  fireEvent.click(await screen.findByText('添加自选股'));
  expect(open).toHaveBeenCalled();
  expect(screen.getByText('半导体')).toBeInTheDocument();
});
test('does not turn a data error into an empty watchlist', async () => {
  vi.mocked(fetch).mockRejectedValue(new Error('offline'));
  render(
    <PersonalMarketPanel
      businessDate="2026-09-30"
      sectors={sectors}
      addToast={vi.fn()}
    />,
  );
  await screen.findByText(/自选关注暂未加载/);
  expect(screen.queryByText('从你的第一只自选开始')).not.toBeInTheDocument();
});
test('adds a same-date discovery candidate and removes the add action', async () => {
  vi.mocked(fetch).mockImplementation(async (url) =>
    response(
      String(url).includes('/focuses')
        ? [focus]
        : String(url).includes('/personal?')
          ? []
          : String(url).includes('latest')
            ? {
                report: {
                  as_of_date: '2026-09-30',
                  candidates: [
                    {
                      code: '600002',
                      name: '候选公司',
                      admitted: true,
                      sector_names: ['电网设备'],
                    },
                  ],
                },
              }
            : {},
    ),
  );
  render(
    <PersonalMarketPanel
      businessDate="2026-09-30"
      sectors={sectors}
      addToast={vi.fn()}
    />,
  );
  fireEvent.click(await screen.findByText('加入自选'));
  await waitFor(() =>
    expect(fetch).toHaveBeenCalledWith(
      '/api/watchlist',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          code: '600002',
          type: 'STOCK',
          groupName: '市场研究',
        }),
      }),
    ),
  );
  await waitFor(() =>
    expect(screen.queryByText('加入自选')).not.toBeInTheDocument(),
  );
});
test('sector links use explicit directions and retain unrelated opportunities', () => {
  expect(industryChanges(sectors, [focus], '2026-09-30')).toHaveLength(1);
  expect(
    industryChanges(sectors, [{ ...focus, direction: '设备' }], '2026-09-30'),
  ).toHaveLength(0);
  expect(opportunitySectors(sectors, [focus]).map((s) => s.sectorName)).toEqual(
    ['电网设备', '半导体'],
  );
});

test('stock-relative moves compare same-day percentage points only', () => {
  const research = { businessDate: '2026-09-30', stocks: [{ instrumentCode: '600001.SH', return1d: 5 }] } as import('./marketResearchTypes').DailyResearch;
  expect(relativeChanges(research, sectors, [focus], '2026-09-30')[0].title).toContain('强于电网设备 3.00 个百分点');
  expect(relativeChanges(research, sectors, [focus], '2026-09-29')).toEqual([]);
});
