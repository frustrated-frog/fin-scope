import { expect, test } from 'vitest';
import { reactionPath } from './ReactionChart';
import { sourceHref } from './reactionTypes';

test('draws disconnected paths for missing sessions and suppresses unclosed prices', () => {
  const path = reactionPath([
    { session: 0, tradeDate: '2026-09-17', status: 'READY', stockReturnPct: 0 },
    { session: 1, tradeDate: '2026-09-18', status: 'MISSING_DATA' },
    { session: 2, tradeDate: '2026-09-21', status: 'READY', stockReturnPct: 2 },
    { session: 3, tradeDate: '2026-09-22', status: 'NOT_DUE', stockReturnPct: 9 }
  ], 'stockReturnPct', value => value, value => value);
  expect(path).toContain('M0.00,0.00');
  expect(path).toContain('M2.00,2.00');
  expect(path).not.toContain('L');
  expect(path).not.toContain('9.00');
});

test('allows only web source links', () => {
  expect(sourceHref('javascript:alert(1)')).toBeUndefined();
  expect(sourceHref('file:///private')).toBeUndefined();
  expect(sourceHref('https://example.com/notice')).toBe('https://example.com/notice');
});
