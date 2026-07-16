import { expect, test } from 'vitest';

import type { DragonTigerView } from './marketIntelTypes';
import {
  businessDaysElapsed,
  shouldAutoRefreshDragonTiger
} from './dragonTigerRefreshPolicy';

const baseView: DragonTigerView = {
  instrument: { id: 7, code: '000021', type: 'STOCK', name: '深科技', market: 'SZ' },
  range: { days: 120, from: '2026-03-19', to: '2026-07-16' },
  records: [],
  health: {
    status: 'FRESH_PRIMARY',
    providerCode: 'EASTMONEY_DRAGON_TIGER',
    asOf: '2026-07-16T16:00:00',
    warnings: []
  }
};

test('counts weekdays after the snapshot date without treating a weekend as trading days', () => {
  expect(businessDaysElapsed(
    new Date('2026-07-17T16:00:00'),
    new Date('2026-07-20T10:00:00')
  )).toBe(1);
});

test('automatically refreshes a dimension that has never been refreshed', () => {
  expect(shouldAutoRefreshDragonTiger({
    ...baseView,
    health: { ...baseView.health, status: 'NOT_REFRESHED', asOf: null }
  }, new Date('2026-07-17T10:00:00'))).toBe(true);
});

test('automatically retries a stale fallback once for the current selection', () => {
  expect(shouldAutoRefreshDragonTiger({
    ...baseView,
    health: { ...baseView.health, status: 'STALE_FALLBACK' }
  }, new Date('2026-07-17T10:00:00'))).toBe(true);
});

test('refreshes only after more than one weekday has elapsed', () => {
  expect(shouldAutoRefreshDragonTiger(baseView, new Date('2026-07-17T10:00:00'))).toBe(false);
  expect(shouldAutoRefreshDragonTiger(baseView, new Date('2026-07-20T10:00:00'))).toBe(true);
});

test('does not infer staleness when a successful response has no timestamp', () => {
  expect(shouldAutoRefreshDragonTiger({
    ...baseView,
    health: { ...baseView.health, asOf: null }
  }, new Date('2026-07-20T10:00:00'))).toBe(false);
});
