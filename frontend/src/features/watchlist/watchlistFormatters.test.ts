import { expect, test } from 'vitest';

import { preserveValidQuotes } from './watchlistFormatters';

test('preserves the previous valid quote when a refresh degrades', () => {
  type Quote = {
    code: string;
    name: string;
    price?: number;
    changePct?: number;
    quoteValid: boolean;
    quoteNote?: string;
    qualityStatus?: 'FRESH_PRIMARY' | 'UNAVAILABLE';
    sourceCode?: string;
    warning?: string;
  };
  const previous: Quote[] = [{
    code: 'BK1036', name: '半导体', price: 100, changePct: 2, quoteValid: true,
    qualityStatus: 'FRESH_PRIMARY', sourceCode: 'EASTMONEY'
  }];
  const next: Quote[] = [{
    code: 'BK1036', name: '半导体', quoteValid: false, quoteNote: '超时',
    qualityStatus: 'UNAVAILABLE', sourceCode: 'TENCENT', warning: '所有实时源均不可用'
  }];

  const result = preserveValidQuotes(next, previous, (item) => item.code);

  expect(result.items[0].price).toBe(100);
  expect(result.items[0]).toMatchObject({
    quoteNote: '超时',
    qualityStatus: 'UNAVAILABLE',
    sourceCode: 'TENCENT',
    warning: '所有实时源均不可用'
  });
  expect(result.degradedCount).toBe(1);
});

test('keeps new invalid quotes when no successful snapshot exists', () => {
  const next = [{ code: 'BK1036', quoteValid: false, quoteNote: '暂不可用' }];

  const result = preserveValidQuotes(next, [], (item) => item.code);

  expect(result.items).toEqual(next);
  expect(result.degradedCount).toBe(0);
});
