import { beforeEach, describe, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { clearWatchlistDailyBarCache, loadWatchlistDailyBars } from './watchlistDailyBarCache';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const bars = [{ code: '600519', tradeDate: '2026-08-06', close: 100 }];

describe('watchlistDailyBarCache', () => {
  beforeEach(() => {
    clearWatchlistDailyBarCache();
    vi.mocked(api).mockReset();
  });

  test('reuses one in-flight and resolved request for the same stock in a session', async () => {
    vi.mocked(api).mockResolvedValue(bars as never);
    const now = new Date(2026, 7, 6, 10, 0);

    const first = loadWatchlistDailyBars('600519', { now });
    const second = loadWatchlistDailyBars('600519', { now });

    await expect(first).resolves.toEqual(bars);
    await expect(second).resolves.toEqual(bars);
    expect(api).toHaveBeenCalledTimes(1);
    expect(api).toHaveBeenCalledWith('/api/watchlist/600519/daily-bars?limit=120');
  });

  test('expires at the next local market refresh boundary', async () => {
    vi.mocked(api).mockResolvedValue(bars as never);

    await loadWatchlistDailyBars('600519', { now: new Date(2026, 7, 6, 10, 0) });
    await loadWatchlistDailyBars('600519', { now: new Date(2026, 7, 6, 15, 16) });

    expect(api).toHaveBeenCalledTimes(2);
  });

  test('removes failed requests so a retry can recover', async () => {
    vi.mocked(api).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(bars as never);

    await expect(loadWatchlistDailyBars('600519')).rejects.toThrow('offline');
    await expect(loadWatchlistDailyBars('600519')).resolves.toEqual(bars);
    expect(api).toHaveBeenCalledTimes(2);
  });

  test('force refresh bypasses session cache and refreshes the persistent snapshot', async () => {
    vi.mocked(api).mockResolvedValue(bars as never);

    await loadWatchlistDailyBars('600519');
    await loadWatchlistDailyBars('600519', { force: true });

    expect(api).toHaveBeenNthCalledWith(2, '/api/watchlist/600519/daily-bars?limit=120&refresh=true');
  });
});
