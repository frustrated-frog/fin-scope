import { expect, test } from 'vitest';

import { aggregateMarketDataQuality } from './marketDataQuality';

test('aggregates mixed fresh and stale rows as partial', () => {
  expect(aggregateMarketDataQuality([
    { qualityStatus: 'FRESH_PRIMARY' },
    { qualityStatus: 'STALE_FALLBACK', staleAgeSeconds: 180 }
  ]).status).toBe('PARTIAL_FRESH');
});

test('keeps the largest stale age and de-duplicates warnings', () => {
  const result = aggregateMarketDataQuality([
    { qualityStatus: 'STALE_FALLBACK', staleAgeSeconds: 60, warning: '实时源不可用' },
    { qualityStatus: 'STALE_FALLBACK', staleAgeSeconds: 120, warning: '实时源不可用' }
  ]);

  expect(result.status).toBe('STALE_FALLBACK');
  expect(result.staleAgeSeconds).toBe(120);
  expect(result.warning).toBe('实时源不可用');
});
