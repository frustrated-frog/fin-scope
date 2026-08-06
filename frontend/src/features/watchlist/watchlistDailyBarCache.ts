import { api } from '../../shared/api/client';
import type { DailyBarPoint } from './KlineChart';

interface CacheEntry {
  expiresAt: number;
  request: Promise<DailyBarPoint[]>;
}

const cache = new Map<string, CacheEntry>();

export function loadWatchlistDailyBars(code: string, options: { force?: boolean; now?: Date } = {}) {
  const now = options.now ?? new Date();
  const existing = cache.get(code);
  if (!options.force && existing && existing.expiresAt > now.getTime()) {
    return existing.request;
  }

  const suffix = options.force ? '&refresh=true' : '';
  const request = api<DailyBarPoint[]>(`/api/watchlist/${code}/daily-bars?limit=120${suffix}`)
    .catch((error) => {
      if (cache.get(code)?.request === request) cache.delete(code);
      throw error;
    });
  cache.set(code, { request, expiresAt: nextRefreshBoundary(now).getTime() });
  return request;
}

export function clearWatchlistDailyBarCache() {
  cache.clear();
}

function nextRefreshBoundary(now: Date) {
  const boundary = new Date(now);
  boundary.setHours(15, 15, 0, 0);
  if (boundary.getTime() <= now.getTime()) boundary.setDate(boundary.getDate() + 1);
  return boundary;
}
