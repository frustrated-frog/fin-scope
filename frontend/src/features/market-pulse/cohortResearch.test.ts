import { expect, test } from 'vitest';
import { buildCohortResearch, cohortOutcome, sectorConcentration } from './cohortResearch';
import type { DailyResearch, ResearchStock } from './marketResearchTypes';

function sample(stocks: ResearchStock[]): DailyResearch {
  return { businessDate: '2026-09-30', selectionDate: '2026-09-29', qualityStatus: 'PARTIAL', sourceCode: 'LOCAL_DAILY_BAR_PANEL', sampleCount: stocks.length, stocks, warnings: [],
    groups: [{ code: 'STRONG', label: '强势股', definition: '昨日涨幅至少3%', eligibleCount: stocks.length, memberCount: stocks.length, validCount: 0, members: stocks.map(stock => stock.instrumentCode) }] };
}
function stock(index: number, return1d?: number | null): ResearchStock {
  return { instrumentCode: `60000${index}.SH`, return1d, groupCodes: ['STRONG'] };
}

test('uses distinct return thresholds and excludes missing prices from statistics', () => {
  const research = sample([-4, -1, 0, 1, 2, null, NaN].map((value, index) => stock(index, value)));
  const group = buildCohortResearch(research).cohorts[0];
  expect(group.outcomes).toEqual({ UP: 1, FLAT: 3, DOWN: 1, MISSING: 2 });
  expect(group.validCount).toBe(5);
  expect(group.median).toBe(0);
  expect(group.advanceRatio).toBe(.4);
  expect(group.drawdownRatio).toBe(.2);
  expect(cohortOutcome(stock(1, 1.01))).toBe('UP');
  expect(cohortOutcome(stock(1, -1.01))).toBe('DOWN');
});

test('uses the middle pair for even groups and includes exactly minus three in drawdowns', () => {
  const group = buildCohortResearch(sample([-3, -2, 1, 3, 5, 7].map((value, index) => stock(index, value)))).cohorts[0];
  expect(group.median).toBe(2);
  expect(group.drawdownRatio).toBe(1 / 6);
});

test('keeps missing members visible and suppresses statistics for a small filtered sample', () => {
  const research = sample([stock(1, 3), stock(2, -2)]);
  research.groups[0].members.push('600003.SH');
  const group = buildCohortResearch(research, new Set(['600001', '600003'])).cohorts[0];
  expect(group.members).toHaveLength(2);
  expect(group.outcomes.MISSING).toBe(1);
  expect(group.median).toBeUndefined();
  expect(group.advanceRatio).toBeUndefined();
  expect(group.drawdownRatio).toBeUndefined();
});

test('compares identical securities across both days and separates other and unavailable states', () => {
  const research = sample([
    { ...stock(1, 1), opportunityState: 'EMERGING', previousOpportunityState: 'REPAIRING' },
    { ...stock(2, 2), opportunityState: 'CONTINUING', previousOpportunityState: 'EMERGING' },
    { ...stock(3, -2), opportunityState: 'OTHER', previousOpportunityState: 'WEAKENING' },
    { ...stock(4, 3), opportunityState: 'EMERGING', previousOpportunityState: null },
    stock(5),
  ]);
  const data = buildCohortResearch(research);
  expect(data.comparableCount).toBe(3);
  expect(data.otherCount).toBe(1);
  expect(data.unavailableCount).toBe(2);
  expect(data.opportunities.map(item => [item.state, item.members.length, item.change])).toEqual([
    ['EMERGING', 1, 0], ['CONTINUING', 1, 1], ['REPAIRING', 0, -1], ['WEAKENING', 0, -1],
  ]);
  const watched = buildCohortResearch(research, new Set(['600001']));
  expect(watched.comparableCount).toBe(1);
  expect(watched.opportunities[0].change).toBe(1);
});

test('an older response has no inferred opportunity counts', () => {
  const data = buildCohortResearch(sample([stock(1, 10)]));
  expect(data.comparableCount).toBe(0);
  expect(data.opportunities.every(item => item.change === undefined)).toBe(true);
});

test('counts each current industry only once per stock while preserving multiple memberships', () => {
  expect(sectorConcentration([
    { ...stock(1), sectorNames: ['半导体', '半导体', '设备'] },
    { ...stock(2), sectorNames: ['半导体'] },
  ])).toEqual([['半导体', 2], ['设备', 1]]);
});
