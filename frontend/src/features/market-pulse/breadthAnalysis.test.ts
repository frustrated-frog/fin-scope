import { expect, test } from 'vitest';
import { breadthChanges, bucketRatio, deltaText, difference, directionalParticipation, highLowRows, indexDivergence, participationGap, shareText, tailShare, trendRows } from './breadthAnalysis';

test('compares participation with the same denominator, excluding flat stocks', () => {
  const breadth = { advanceCount: 40, declineCount: 10, flatCount: 50, advanceRatio: .4, volumePressure: { advanceAmountRatio: .8 } };
  expect(directionalParticipation(breadth)).toBe(.8);
  expect(participationGap(breadth)).toBe(0);
  expect(directionalParticipation({ advanceCount: 0, declineCount: 0 })).toBeUndefined();
  expect(participationGap({ advanceCount: 40, declineCount: 10 })).toBeUndefined();
});

test('uses each horizon sample rather than the full market for high and low shares', () => {
  const rows = highLowRows({ validCount: 5000, newHighLow: { high20Count: 20, low20Count: 5, valid20Count: 100, high60Count: 0, low60Count: 10, valid60Count: 50 } });
  expect(rows[0].balance).toBeCloseTo(.15);
  expect(rows[1]).toMatchObject({ highRatio: 0, lowRatio: .2, balance: -.2 });
  expect(rows[2].balance).toBeUndefined();
  expect(highLowRows({ newHighLow: { high20Count: 10, low20Count: 1, valid20Count: 0 } })[0].balance).toBeUndefined();
  expect(highLowRows({ newHighLow: { high20Count: 101, low20Count: 1, valid20Count: 100 } })[0].highRatio).toBeUndefined();
});

test('retains legacy reported MA ratios but does not infer a ratio for an empty sample', () => {
  expect(trendRows({ trendBreadth: { ma20Ratio: .6, ma60Ratio: .3, ma60ValidCount: 0, ma120Ratio: 1.2, ma250Ratio: NaN } }).map(row => row.ratio)).toEqual([.6, undefined, undefined, undefined]);
  expect(shareText(undefined)).toBe('—');
  expect(shareText(NaN)).toBe('—');
  expect(shareText(0)).toBe('0.0%');
});

test('calculates distribution tails only with both bins, including zero and real denominators', () => {
  const buckets = [{ code: 'UP_3_7', label: '3% ~ 7%', count: 20, ratio: .9 }, { code: 'UP_7', label: '≥7%', count: 0, ratio: 0 }];
  expect(tailShare({ validCount: 100, returnDistribution: buckets }, 'UP')).toBe(.2);
  expect(tailShare({ validCount: 100, returnDistribution: buckets.slice(1) }, 'UP')).toBeUndefined();
  expect(bucketRatio(buckets[0], { validCount: 0 })).toBeUndefined();
  expect(bucketRatio(buckets[0], { validCount: 10 })).toBeUndefined();
});

test('ranks comparable changes by magnitude and preserves rising new-low share', () => {
  const rows = breadthChanges(
    { advanceRatio: .5, trendBreadth: { ma20Ratio: .2 }, newHighLow: { high20Count: 2, low20Count: 5, valid20Count: 10 } },
    { advanceRatio: .4, trendBreadth: { ma20Ratio: .5 }, newHighLow: { high20Count: 1, low20Count: 1, valid20Count: 10 } }
  );
  expect(rows[0]).toMatchObject({ label: '20日新低占比', change: .4 });
  expect(rows[1]).toMatchObject({ label: 'MA20 参与度', change: -.3 });
  expect(breadthChanges({ advanceRatio: .4 })).toEqual([]);
});

test('keeps return percentages distinct from ratio differences and never turns missing into zero', () => {
  expect(deltaText(difference(.3, -.1), '个百分点', 1)).toBe('+0.4 个百分点');
  expect(deltaText(difference(.6, .4))).toBe('+20.0 个百分点');
  expect(deltaText(difference(.3, undefined))).toBe('—');
  expect(indexDivergence(.3, -.1)).toBe('指数上涨，中位数下跌');
  expect(indexDivergence(-.3, .1)).toBe('指数下跌，中位数上涨');
  expect(indexDivergence(undefined, 0)).toBe('等待指数与个股数据');
});
