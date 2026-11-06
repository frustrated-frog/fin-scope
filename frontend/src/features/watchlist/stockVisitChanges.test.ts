import { describe, expect, test } from 'vitest';
import type { WatchlistItem } from '../../shared/types';
import { compareStockVisits, createStockVisitSnapshot, parseStockVisitSnapshot } from './stockVisitChanges';

const item: WatchlistItem = { id: 1, code: '600519', type: 'STOCK', quoteValid: true, price: 100, changePct: 1, quoteDate: '2026-11-05', quoteTime: '2026-11-05T15:00:00', attributionReportId: 1, attributionReportDate: '2026-11-04' };

describe('watchlist stock visit comparisons', () => {
  test('first visit establishes a baseline and excludes funds and newly added stocks from changes', () => {
    const first = createStockVisitSnapshot([item, { ...item, code: '021894', type: 'FUND' }]);
    expect(Object.keys(first.stocks)).toEqual(['600519']);
    expect(compareStockVisits(undefined, first)).toEqual([]);
    const next = createStockVisitSnapshot([item, { ...item, code: '688012' }]);
    expect(compareStockVisits(first, next)).toEqual([]);
  });

  test('separates price change since last visit, entering abnormal range and a new report', () => {
    const first = createStockVisitSnapshot([item]);
    const next = createStockVisitSnapshot([{ ...item, price: 106, changePct: 6, attributionReportId: 2, attributionReportDate: '2026-11-05' }], first);
    const changes = compareStockVisits(first, next);
    expect(changes.map((change) => change.kind)).toEqual(['PRICE', 'ABNORMAL', 'REPORT']);
    expect(changes[0].detail).toContain('+6.00%');
    expect(changes[1].detail).toContain('异动区间');
    expect(changes[2].detail).toContain('2026-11-05');
  });

  test('does not replace a valid baseline with unavailable stale or older quotes', () => {
    const first = createStockVisitSnapshot([item]);
    for (const update of [
      { ...item, quoteValid: false, price: 120 },
      { ...item, qualityStatus: 'STALE_FALLBACK' as const, price: 120 },
      { ...item, quoteDate: '2026-11-04', quoteTime: '2026-11-04T15:00:00', price: 120 }
    ]) {
      const next = createStockVisitSnapshot([update], first);
      expect(next.stocks['600519'].price).toBe(100);
      expect(compareStockVisits(first, next)).toEqual([]);
    }
  });

  test('unchanged already-abnormal prices and older report dates do not generate new events', () => {
    const first = createStockVisitSnapshot([{ ...item, changePct: 6 }]);
    const next = createStockVisitSnapshot([{ ...item, changePct: 7, attributionReportId: 3, attributionReportDate: '2026-11-03' }], first);
    expect(compareStockVisits(first, next)).toEqual([]);
  });

  test('a new report is visible even when quotes fail, and older report ids do not regress the baseline', () => {
    const first = createStockVisitSnapshot([item]);
    const next = createStockVisitSnapshot([{ ...item, quoteValid: false, attributionReportId: 2, attributionReportDate: '2026-11-05' }], first);
    expect(next.stocks['600519'].price).toBe(100);
    expect(compareStockVisits(first, next).map((change) => change.kind)).toEqual(['REPORT']);
    const older = createStockVisitSnapshot([{ ...item, attributionReportId: 1, attributionReportDate: '2026-11-05' }], next);
    expect(older.stocks['600519'].reportId).toBe(2);
    expect(compareStockVisits(next, older)).toEqual([]);
  });

  test('corrupt storage and incompatible versions recover without breaking the page', () => {
    expect(parseStockVisitSnapshot('{oops')).toBeUndefined();
    expect(parseStockVisitSnapshot(JSON.stringify({ version: 2, stocks: {} }))).toBeUndefined();
    expect(parseStockVisitSnapshot(JSON.stringify({ version: 1, recordedAt: '2026-11-05', stocks: { '600519': { price: 'bad' } } }))).toBeUndefined();
    const baseline = createStockVisitSnapshot([item]);
    expect(parseStockVisitSnapshot(JSON.stringify(baseline))).toEqual(baseline);
  });
});
