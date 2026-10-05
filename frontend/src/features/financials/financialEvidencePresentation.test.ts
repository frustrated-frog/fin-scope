import { expect, test } from 'vitest';
import { formatEvidenceValue, formatMetric } from './financialPresentation';

test('preserves currency while scaling monetary evidence and distinguishes ratios from percentage-point changes', () => {
  expect(formatEvidenceValue({ id: 'L', type: 'LINE_ITEM', label: '收入', value: '2202460295.29', unit: 'USD' })).toBe('22.02亿 USD');
  expect(formatEvidenceValue({ id: 'M', type: 'METRIC', label: '速动比率', value: '1.25', unit: '倍' })).toBe('1.25倍');
  expect(formatMetric('5.12', 'pct')).toBe('5.12 个百分点');
  expect(formatEvidenceValue({ id: 'G', type: 'DATA_GAP', label: '缺口' })).toBe('—');
});
