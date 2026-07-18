import { expect, test } from 'vitest';

import { reportPeriodEnd } from './financialPresentation';
import { FinancialReportType } from './financialTypes';

test.each<[FinancialReportType, string]>([
  ['Q1', '2026-03-31'],
  ['HALF_YEAR', '2026-06-30'],
  ['Q3', '2026-09-30'],
  ['ANNUAL', '2026-12-31']
])('derives the canonical period end for %s reports', (reportType, expected) => {
  expect(reportPeriodEnd('2026', reportType)).toBe(expected);
});

test('does not derive a period end from an incomplete year', () => {
  expect(reportPeriodEnd('26', 'Q1')).toBe('');
});
