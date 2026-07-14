import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { useWatchlistDashboardData } from './useWatchlistDashboardData';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const overview = {
  category: 'INDUSTRY' as const,
  qualityStatus: 'FRESH' as const,
  retrievedAt: '2026-07-14T10:00:00',
  leaders: [],
  laggards: []
};

beforeEach(() => vi.mocked(api).mockReset());

test('keeps investments and indices ready when sector overview fails', async () => {
  vi.mocked(api).mockImplementation((path?: string) => {
    if (!path) return Promise.resolve([]) as never;
    if (path.startsWith('/api/sector-market/overview')) return Promise.reject(new Error('sector down'));
    if (path === '/api/watchlist') return Promise.resolve([
      { id: 1, code: '600519', type: 'STOCK', quoteValid: true },
      { id: 2, code: 'BK1036', type: 'SECTOR', quoteValid: true }
    ]) as never;
    if (path === '/api/market-indices' || path === '/api/sector-market/follows') return Promise.resolve([]) as never;
    return Promise.resolve(overview) as never;
  });

  const { result } = renderHook(() => useWatchlistDashboardData());

  await waitFor(() => expect(result.current.investments.phase).toBe('ready'));
  expect(result.current.investments.data.map((item) => item.code)).toEqual(['600519']);
  expect(result.current.indices.phase).toBe('ready');
  expect(result.current.sectorOverview.phase).toBe('error');
  expect(vi.mocked(api).mock.calls.every(([path]) => typeof path === 'string')).toBe(true);
});

test('switches sector category without reloading independent resources', async () => {
  vi.mocked(api).mockImplementation((path?: string) => {
    if (!path) return Promise.resolve([]) as never;
    if (path.startsWith('/api/sector-market/overview')) {
      const category = path.includes('CONCEPT') ? 'CONCEPT' : 'INDUSTRY';
      return Promise.resolve({ ...overview, category }) as never;
    }
    return Promise.resolve([]) as never;
  });
  const { result } = renderHook(() => useWatchlistDashboardData());
  await waitFor(() => expect(result.current.sectorOverview.phase).toBe('ready'));
  vi.mocked(api).mockClear();

  result.current.setSectorCategory('CONCEPT');

  await waitFor(() => expect(result.current.sectorOverview.data.category).toBe('CONCEPT'));
  expect(api).toHaveBeenCalledTimes(1);
  expect(api).toHaveBeenCalledWith('/api/sector-market/overview?category=CONCEPT&limit=5');
});

test('ignores an older category response that arrives after the current one', async () => {
  let resolveIndustry: (value: unknown) => void = () => undefined;
  vi.mocked(api).mockImplementation((path?: string) => {
    if (path?.includes('category=INDUSTRY')) {
      return new Promise((resolve) => { resolveIndustry = resolve; }) as never;
    }
    if (path?.includes('category=CONCEPT')) {
      return Promise.resolve({ ...overview, category: 'CONCEPT' }) as never;
    }
    return Promise.resolve([]) as never;
  });
  const { result } = renderHook(() => useWatchlistDashboardData());

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/sector-market/overview?category=INDUSTRY&limit=5'));
  result.current.setSectorCategory('CONCEPT');
  await waitFor(() => expect(result.current.sectorOverview.data.category).toBe('CONCEPT'));
  await act(async () => { resolveIndustry(overview); });

  expect(result.current.sectorOverview.data.category).toBe('CONCEPT');
});

test('keeps the last ranking visible when a later refresh fails', async () => {
  const populated = {
    ...overview,
    leaders: [{ code: 'BK1036', name: '半导体', category: 'INDUSTRY' as const, changePct: 2.6 }]
  };
  vi.mocked(api).mockImplementation((path?: string) => Promise.resolve(
    path?.startsWith('/api/sector-market/overview') ? populated : []
  ) as never);
  const { result } = renderHook(() => useWatchlistDashboardData());
  await waitFor(() => expect(result.current.sectorOverview.data.leaders).toHaveLength(1));

  vi.mocked(api).mockImplementation((path?: string) => path?.startsWith('/api/sector-market/overview')
    ? Promise.reject(new Error('upstream timeout'))
    : Promise.resolve([]) as never);
  await act(async () => { await result.current.loadSectorOverview('INDUSTRY', true); });

  expect(result.current.sectorOverview.phase).toBe('ready');
  expect(result.current.sectorOverview.data.leaders[0].code).toBe('BK1036');
  expect(result.current.sectorOverview.warning).toContain('upstream timeout');
});
