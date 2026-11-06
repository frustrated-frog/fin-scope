import type { WatchlistItem } from '../../shared/types';

export type StockVisitSnapshot = {
  version: 1;
  recordedAt: string;
  stocks: Record<string, { price?: number; changePct?: number; quoteTime?: string; quoteDate?: string; reportId?: number; reportDate?: string }>;
};

export type StockVisitChange = { code: string; kind: 'PRICE' | 'ABNORMAL' | 'REPORT'; detail: string };

export function createStockVisitSnapshot(items: WatchlistItem[], previous?: StockVisitSnapshot, now = new Date()): StockVisitSnapshot {
  const stocks: StockVisitSnapshot['stocks'] = {};
  for (const item of items.filter((value) => value.type === 'STOCK').slice(0, 500)) {
    const old = previous?.stocks[item.code];
    const hasNewReport = item.attributionReportId != null && Number.isSafeInteger(item.attributionReportId)
      && (old?.reportId == null || item.attributionReportId > old.reportId)
      && (!old?.reportDate || (item.attributionReportDate != null && item.attributionReportDate >= old.reportDate));
    const report = hasNewReport
      ? { reportId: item.attributionReportId, reportDate: item.attributionReportDate }
      : { reportId: old?.reportId, reportDate: old?.reportDate };
    if (!hasUsableQuote(item) || (old?.quoteTime && item.quoteTime && item.quoteTime < old.quoteTime)
      || (old?.quoteDate && item.quoteDate && item.quoteDate < old.quoteDate)) {
      if (old || hasNewReport) {
        stocks[item.code] = { ...old, ...report };
      }
      continue;
    }
    stocks[item.code] = {
      price: item.price, changePct: item.changePct, quoteTime: item.quoteTime, quoteDate: item.quoteDate,
      ...report
    };
  }
  return { version: 1, recordedAt: now.toISOString(), stocks };
}

export function compareStockVisits(previous: StockVisitSnapshot | undefined, current: StockVisitSnapshot): StockVisitChange[] {
  if (!previous) {
    return [];
  }
  const changes: StockVisitChange[] = [];
  for (const [code, latest] of Object.entries(current.stocks)) {
    const old = previous.stocks[code];
    if (!old) {
      continue;
    }
    if (old.price != null && old.price > 0 && latest.price != null && latest.price !== old.price) {
      const change = (latest.price / old.price - 1) * 100;
      changes.push({ code, kind: 'PRICE', detail: `较上次查看 ${change > 0 ? '+' : ''}${change.toFixed(2)}%（${old.price.toFixed(2)} → ${latest.price.toFixed(2)}）` });
    }
    if (old.changePct != null && latest.changePct != null && Math.abs(old.changePct) < 5 && Math.abs(latest.changePct) >= 5) {
      changes.push({ code, kind: 'ABNORMAL', detail: `进入日涨跌幅 ≥ 5% 的异动区间（${latest.changePct.toFixed(2)}%）` });
    }
    if (latest.reportId != null && (old.reportId == null || latest.reportId > old.reportId)
      && (!old.reportDate || (latest.reportDate != null && latest.reportDate >= old.reportDate))) {
      changes.push({ code, kind: 'REPORT', detail: `有新的归因报告${latest.reportDate ? ` · ${latest.reportDate}` : ''}` });
    }
  }
  return changes;
}

export function parseStockVisitSnapshot(raw: string | null): StockVisitSnapshot | undefined {
  if (!raw) {
    return undefined;
  }
  try {
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const candidate = value as Partial<StockVisitSnapshot>;
    if (candidate.version !== 1 || typeof candidate.recordedAt !== 'string' || !Number.isFinite(Date.parse(candidate.recordedAt))
      || !candidate.stocks || typeof candidate.stocks !== 'object' || Array.isArray(candidate.stocks)) {
      return undefined;
    }
    const entries = Object.entries(candidate.stocks);
    if (entries.length > 500 || entries.some(([code, item]) => !/^\d{6}$/.test(code) || !item || typeof item !== 'object'
      || (item.price != null && (typeof item.price !== 'number' || !Number.isFinite(item.price) || item.price <= 0))
      || (item.changePct != null && (typeof item.changePct !== 'number' || !Number.isFinite(item.changePct)))
      || [item.quoteDate, item.quoteTime, item.reportDate].some((date) => date != null && typeof date !== 'string')
      || (item.reportId != null && !Number.isSafeInteger(item.reportId)))) {
      return undefined;
    }
    return candidate as StockVisitSnapshot;
  } catch {
    return undefined;
  }
}

function hasUsableQuote(item: WatchlistItem) {
  return item.quoteValid && item.price != null && Number.isFinite(item.price) && item.price > 0
    && item.qualityStatus !== 'UNAVAILABLE' && item.qualityStatus !== 'STALE_FALLBACK';
}
