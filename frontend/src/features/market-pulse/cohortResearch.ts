import { finite } from './marketResearch';
import { stockCode } from './personalMarket';
import type { DailyResearch, OpportunityState, ResearchStock } from './marketResearchTypes';

export const outcomeLabels = { ALL: '全部', UP: '上涨', FLAT: '横盘', DOWN: '回落', MISSING: '缺行情' } as const;
export type CohortOutcome = keyof typeof outcomeLabels;
export const opportunityLabels: Record<OpportunityState, string> = {
  EMERGING: '新转强', CONTINUING: '持续强势', REPAIRING: '弱势修复', WEAKENING: '强势转弱', OTHER: '其他走势',
};
export const opportunityDescriptions: Record<OpportunityState, string> = {
  EMERGING: '昨日未满足趋势条件，今日开始满足',
  CONTINUING: '连续两个交易日满足趋势条件',
  REPAIRING: '两日均未满足趋势条件，今日收涨',
  WEAKENING: '昨日满足趋势条件，今日不再满足',
  OTHER: '两日均未满足趋势条件，今日未收涨',
};
export const featuredOpportunities: OpportunityState[] = ['EMERGING', 'CONTINUING', 'REPAIRING', 'WEAKENING'];

export function cohortOutcome(stock: ResearchStock): Exclude<CohortOutcome, 'ALL'> {
  if (!finite(stock.return1d)) {
    return 'MISSING';
  }
  if (stock.return1d > 1) {
    return 'UP';
  }
  if (stock.return1d < -1) {
    return 'DOWN';
  }
  return 'FLAT';
}

export function buildCohortResearch(research: DailyResearch, watched?: Set<string>) {
  const included = (code: string) => !watched || watched.has(stockCode(code));
  const stocks = research.stocks.filter(stock => included(stock.instrumentCode));
  const byCode = new Map(stocks.map(stock => [stock.instrumentCode, stock]));
  const cohorts = research.groups.map(group => {
    const members: ResearchStock[] = group.members.filter(included).map(code => byCode.get(code) ?? { instrumentCode: code, groupCodes: [group.code] });
    const values = members.map(stock => stock.return1d).filter(finite).sort((a, b) => a - b);
    const ready = values.length >= 5;
    const middle = Math.floor(values.length / 2);
    const median = ready ? (values.length % 2 ? values[middle] : (values[middle - 1] + values[middle]) / 2) : undefined;
    const outcomes = { UP: 0, FLAT: 0, DOWN: 0, MISSING: 0 };
    for (const stock of members) {
      outcomes[cohortOutcome(stock)] += 1;
    }
    return { ...group, members, validCount: values.length, median, ready, outcomes,
      advanceRatio: ready ? values.filter(value => value > 0).length / values.length : undefined,
      drawdownRatio: ready ? values.filter(value => value <= -3).length / values.length : undefined };
  });
  // Day-over-day counts use the same securities on both days, avoiding changes in sample coverage.
  const comparable = stocks.filter(stock => stock.opportunityState && stock.previousOpportunityState);
  const opportunities = featuredOpportunities.map(state => {
    const members = comparable.filter(stock => stock.opportunityState === state);
    const previousCount = comparable.filter(stock => stock.previousOpportunityState === state).length;
    return { state, members, previousCount, change: comparable.length ? members.length - previousCount : undefined };
  });
  return { stocks, cohorts, opportunities, comparableCount: comparable.length,
    otherCount: comparable.filter(stock => stock.opportunityState === 'OTHER').length,
    unavailableCount: stocks.length - comparable.length };
}

export function sectorConcentration(stocks: ResearchStock[]) {
  const counts = new Map<string, number>();
  for (const stock of stocks) {
    for (const sector of new Set(stock.sectorNames ?? [])) {
      counts.set(sector, (counts.get(sector) ?? 0) + 1);
    }
  }
  return [...counts].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'zh-CN')).slice(0, 3);
}
