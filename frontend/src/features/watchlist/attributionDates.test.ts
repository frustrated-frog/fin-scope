import { expect, test } from 'vitest';
import { recentAttributionDates } from './attributionDates';

test('uses Beijing midnight and keeps three consecutive calendar days across years', () => {
  expect(recentAttributionDates(new Date('2026-12-31T16:30:00Z'))).toEqual([
    { label: '今天', date: '2027-01-01' },
    { label: '昨天', date: '2026-12-31' },
    { label: '前天', date: '2026-12-30' }
  ]);
});

test('keeps weekends in the calendar window rather than expanding to earlier trading days', () => {
  expect(recentAttributionDates(new Date('2026-09-28T04:00:00Z')).map(item => item.date))
    .toEqual(['2026-09-28', '2026-09-27', '2026-09-26']);
});
