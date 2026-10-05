import { expect, test } from 'vitest';
import { evidenceTypeLabel, formatEvidenceValue, formatMetric, readableFinancialText } from './financialPresentation';

test('preserves currency while scaling monetary evidence and distinguishes ratios from percentage-point changes', () => {
  expect(formatEvidenceValue({ id: 'L', type: 'LINE_ITEM', label: '收入', value: '2202460295.29', unit: 'USD' })).toBe('22.02亿 USD');
  expect(formatEvidenceValue({ id: 'M', type: 'METRIC', label: '速动比率', value: '1.25', unit: '倍' })).toBe('1.25倍');
  expect(formatMetric('5.12', 'pct')).toBe('5.12 个百分点');
  expect(formatEvidenceValue({ id: 'G', type: 'DATA_GAP', label: '缺口' })).toBe('—');
});

test('explains source and period markers without changing reported numbers or evidence ids', () => {
  expect(readableFinancialText('2026Q1收入1200.12，口径=CURRENT_YTD；来源=REPORTED')).toBe('2026Q1收入1200.12，口径=本期累计；来源=公开披露');
  expect(readableFinancialText('L_INCOME_REVENUE_2026_Q1_CURRENT_YTD')).toBe('L_INCOME_REVENUE_2026_Q1_CURRENT_YTD');
  expect(evidenceTypeLabel('LINE_ITEM')).toBe('报表科目');
});
