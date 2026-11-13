import { expect, test } from 'vitest';
import { fundamentalChange, fundamentalReviewDate } from './fundamentalResearchTypes';

test('review dates follow Shanghai calendar even before the UTC date changes', () => {
  const now = new Date('2026-11-13T17:00:00Z');
  expect(fundamentalReviewDate(0, now)).toBe('2026-11-14');
  expect(fundamentalReviewDate(30, now)).toBe('2026-12-14');
});

test('ratio differences are percentage points and cash differences retain signed currency units', () => {
  expect(fundamentalChange('5.2', '%')).toBe('+5.2 个百分点');
  expect(fundamentalChange('-200000000', 'CNY')).toBe('-2.00 亿元');
});
