import { api } from '../../shared/api/client';
import type { DailyBarPoint } from './KlineChart';

interface CacheEntry {
  expiresAt: number;
  request: Promise<StockMarketHistory>;
}

const cache = new Map<string, CacheEntry>();

export type DailyBarHistory = {
  code: string;
  market: string;
  sourceCode?: string;
  qualityStatus?: string;
  asOf?: string;
  retrievedAt?: string;
  bars: DailyBarPoint[];
  warnings: string[];
};

export type StockMarketHistory = {
  stock: DailyBarHistory;
  benchmark?: DailyBarHistory | null;
  benchmarkCode: string;
  benchmarkName: string;
  warnings: string[];
};

export function loadWatchlistMarketHistory(code: string, options: { force?: boolean; now?: Date } = {}) {
  const now = options.now ?? new Date();
  const existing = cache.get(code);
  if (!options.force && existing && existing.expiresAt > now.getTime()) {
    return existing.request;
  }

  const suffix = options.force ? '?refresh=true' : '';
  const request = api<StockMarketHistory>(`/api/watchlist/stocks/${encodeURIComponent(code)}/market-history${suffix}`)
    .then((history) => {
      if (!history?.stock || !Array.isArray(history.stock.bars)) {
        throw new Error('股票历史响应不完整，请重试');
      }
      return history;
    })
    .catch((error) => {
      if (cache.get(code)?.request === request) {
        cache.delete(code);
      }
      throw error;
    });
  if (cache.size >= 128 && !cache.has(code)) {
    cache.delete(cache.keys().next().value!);
  }
  cache.set(code, { request, expiresAt: nextRefreshBoundary(now).getTime() });
  return request;
}

export function clearWatchlistDailyBarCache() {
  cache.clear();
}

function nextRefreshBoundary(now: Date) {
  // 短缓存同时覆盖跨交易日与来源恢复，不依赖浏览器所在时区。
  return new Date(now.getTime() + 5 * 60_000);
}
