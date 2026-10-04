import type { MarketBreadth, MarketReturnDistributionBucket } from './marketPulseTypes';
import { finite } from './panoramaModel';

export function validRatio(value: unknown): value is number {
  return finite(value) && value >= 0 && value <= 1;
}
export function validCount(value: unknown): value is number {
  return finite(value) && Number.isInteger(value) && value >= 0;
}
export function numberText(value: unknown, digits = 1) {
  return finite(value) ? value.toLocaleString('zh-CN', { maximumFractionDigits: digits }) : '—';
}
export function shareText(value: unknown, digits = 1) {
  return validRatio(value) ? `${(value * 100).toFixed(digits)}%` : '—';
}
export function difference(current: unknown, previous: unknown) {
  return finite(current) && finite(previous) ? current - previous : undefined;
}
export function deltaText(value: unknown, unit = '个百分点', scale = 100) {
  if (!finite(value)) {
    return '—';
  }
  const rounded = Math.round(value * scale * 10) / 10;
  return `${rounded > 0 ? '+' : ''}${rounded.toFixed(1)} ${unit}`;
}
export function directionalParticipation(breadth?: MarketBreadth) {
  const up = breadth?.advanceCount;
  const down = breadth?.declineCount;
  return validCount(up) && validCount(down) && up + down > 0 ? up / (up + down) : undefined;
}
export function participationGap(breadth?: MarketBreadth) {
  const amount = breadth?.volumePressure?.advanceAmountRatio;
  return difference(validRatio(amount) ? amount : undefined, directionalParticipation(breadth));
}
export function trendRows(breadth?: MarketBreadth) {
  const trend = breadth?.trendBreadth;
  return ([20, 60, 120, 250] as const).map(window => {
    const ratio = trend?.[`ma${window}Ratio`];
    const count = trend?.[`ma${window}ValidCount`];
    return { window, count: validCount(count) ? count : undefined,
      ratio: validRatio(ratio) && (count == null || (validCount(count) && count > 0)) ? ratio : undefined };
  });
}
export function highLowRows(breadth?: MarketBreadth) {
  const values = breadth?.newHighLow;
  return ([20, 60, 250] as const).map(window => {
    const rawHigh = values?.[`high${window}Count`];
    const rawLow = values?.[`low${window}Count`];
    const rawValid = values?.[`valid${window}Count`];
    const count = validCount(rawValid) && rawValid > 0 ? rawValid : undefined;
    const high = validCount(rawHigh) && (count == null || rawHigh <= count) ? rawHigh : undefined;
    const low = validCount(rawLow) && (count == null || rawLow <= count) ? rawLow : undefined;
    const highRatio = count && high != null ? high / count : undefined;
    const lowRatio = count && low != null ? low / count : undefined;
    return { window, count, high, low, highRatio, lowRatio, balance: difference(highRatio, lowRatio) };
  });
}
export function bucketRatio(bucket?: MarketReturnDistributionBucket, breadth?: MarketBreadth) {
  if (!bucket || !validCount(bucket.count)) {
    return undefined;
  }
  if (breadth?.validCount != null) {
    return validCount(breadth.validCount) && breadth.validCount > 0 && bucket.count <= breadth.validCount ? bucket.count / breadth.validCount : undefined;
  }
  return validRatio(bucket.ratio) ? bucket.ratio : undefined;
}
export function tailShare(breadth: MarketBreadth | undefined, direction: 'UP' | 'DOWN') {
  const buckets = [`${direction}_3_7`, `${direction}_7`].map(code => breadth?.returnDistribution?.find(row => row.code === code));
  const shares = buckets.map(bucket => bucketRatio(bucket, breadth));
  return shares.every(validRatio) && shares[0]! + shares[1]! <= 1 ? shares[0]! + shares[1]! : undefined;
}
export type BreadthChange = { label: string; current: number; previous: number; change: number };
export function breadthChanges(current?: MarketBreadth, previous?: MarketBreadth): BreadthChange[] {
  const trends = trendRows(current);
  const priorTrends = trendRows(previous);
  const highs = highLowRows(current);
  const priorHighs = highLowRows(previous);
  const rows = [
    { label: '上涨家数占比', current: current?.advanceRatio, previous: previous?.advanceRatio },
    { label: '上涨成交额占比', current: current?.volumePressure?.advanceAmountRatio, previous: previous?.volumePressure?.advanceAmountRatio },
    ...trends.map((row, i) => ({ label: `MA${row.window} 参与度`, current: row.ratio, previous: priorTrends[i].ratio })),
    ...highs.map((row, i) => ({ label: `${row.window}日新高占比`, current: row.highRatio, previous: priorHighs[i].highRatio })),
    ...highs.map((row, i) => ({ label: `${row.window}日新低占比`, current: row.lowRatio, previous: priorHighs[i].lowRatio })),
  ];
  return rows.filter((row): row is { label: string; current: number; previous: number } => validRatio(row.current) && validRatio(row.previous))
    .map(row => ({ ...row, change: row.current - row.previous })).sort((a, b) => Math.abs(b.change) - Math.abs(a.change));
}
export function indexDivergence(indexReturn: unknown, medianReturn: unknown) {
  if (!finite(indexReturn) || !finite(medianReturn)) {
    return '等待指数与个股数据';
  }
  if (indexReturn > 0 && medianReturn < 0) {
    return '指数上涨，中位数下跌';
  }
  if (indexReturn < 0 && medianReturn > 0) {
    return '指数下跌，中位数上涨';
  }
  return indexReturn === medianReturn ? '指数与中位数持平' : indexReturn > medianReturn ? '指数强于个股中位数' : '个股中位数强于指数';
}
