import { expect, test } from 'vitest';

import { preserveValidQuotes } from './watchlistFormatters';

test('preserves the previous valid quote when a refresh degrades', () => {
  type Quote = { code: string; name: string; price?: number; changePct?: number; quoteValid: boolean; quoteNote?: string };
  const previous: Quote[] = [{ code: 'BK1036', name: '半导体', price: 100, changePct: 2, quoteValid: true }];
  const next: Quote[] = [{ code: 'BK1036', name: '半导体', quoteValid: false, quoteNote: '超时' }];

  const result = preserveValidQuotes(next, previous, (item) => item.code);

  expect(result.items[0].price).toBe(100);
  expect(result.degradedCount).toBe(1);
});

test('keeps new invalid quotes when no successful snapshot exists', () => {
  const next = [{ code: 'BK1036', quoteValid: false, quoteNote: '暂不可用' }];

  const result = preserveValidQuotes(next, [], (item) => item.code);

  expect(result.items).toEqual(next);
  expect(result.degradedCount).toBe(0);
});
