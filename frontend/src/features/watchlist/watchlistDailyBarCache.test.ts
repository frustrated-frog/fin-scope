import { beforeEach, describe, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { clearWatchlistDailyBarCache, loadWatchlistMarketHistory } from './watchlistDailyBarCache';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const bars = [{ code: '600519', tradeDate: '2026-08-06', close: 100 }];
const history = { stock: { code: '600519', market: 'SH', bars, warnings: [] }, benchmarkCode: '000300.SH', benchmarkName: '沪深300', warnings: [] };

describe('watchlistDailyBarCache', () => {
  beforeEach(() => {
    clearWatchlistDailyBarCache();
    vi.mocked(api).mockReset();
  });

  test('reuses one in-flight and resolved request for the same stock in a session', async () => {
    vi.mocked(api).mockResolvedValue(history as never);
    const now = new Date(2026, 7, 6, 10, 0);

    const first = loadWatchlistMarketHistory('600519', { now });
    const second = loadWatchlistMarketHistory('600519', { now });

    await expect(first).resolves.toEqual(history);
    await expect(second).resolves.toEqual(history);
    expect(api).toHaveBeenCalledTimes(1);
    expect(api).toHaveBeenCalledWith('/api/watchlist/stocks/600519/market-history');
  });

  test('expires after five minutes so recovered sources and new daily snapshots can appear', async () => {
    vi.mocked(api).mockResolvedValue(history as never);

    await loadWatchlistMarketHistory('600519', { now: new Date(2026, 7, 6, 10, 0) });
    await loadWatchlistMarketHistory('600519', { now: new Date(2026, 7, 6, 10, 6) });

    expect(api).toHaveBeenCalledTimes(2);
  });

  test('removes failed requests so a retry can recover', async () => {
    vi.mocked(api).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(history as never);

    await expect(loadWatchlistMarketHistory('600519')).rejects.toThrow('offline');
    await expect(loadWatchlistMarketHistory('600519')).resolves.toEqual(history);
    expect(api).toHaveBeenCalledTimes(2);
  });

  test('force refresh bypasses session cache and refreshes the persistent snapshot', async () => {
    vi.mocked(api).mockResolvedValue(history as never);

    await loadWatchlistMarketHistory('600519');
    await loadWatchlistMarketHistory('600519', { force: true });

    expect(api).toHaveBeenNthCalledWith(2, '/api/watchlist/stocks/600519/market-history?refresh=true');
  });

  test('does not cache malformed responses as a successful empty history', async () => {
    vi.mocked(api).mockResolvedValueOnce([] as never).mockResolvedValueOnce(history as never);
    await expect(loadWatchlistMarketHistory('600519')).rejects.toThrow('响应不完整');
    await expect(loadWatchlistMarketHistory('600519')).resolves.toEqual(history);
  });
});
