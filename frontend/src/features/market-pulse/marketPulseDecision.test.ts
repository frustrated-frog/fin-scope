import { describe, expect, test } from 'vitest';

import { buildMarketTransitionDecision } from './marketPulseDecision';
import type { MarketPulseWorkspace } from './marketPulseTypes';

const repairingWorkspace: MarketPulseWorkspace = {
  businessDate: '2026-08-21',
  qualityStatus: 'READY',
  regime: {
    marketStage: 'POST_SELL_OFF_REPAIR',
    riskAppetiteState: 'NEUTRAL',
    liquidityState: 'NORMAL',
    rotationState: 'FAST',
    confidenceScore: 78,
    features: { amountRatio5To20: 1.04, sectorDispersion: 0.026, volatility20: 0.21 }
  },
  breadth: {
    advanceRatio: 0.64,
    medianChangePct: 0.82,
    trendBreadth: { ma20Ratio: 0.61, ma60Ratio: 0.55 },
    newHighLow: { high20Count: 92, low20Count: 21 },
    volumePressure: { advanceAmountRatio: 0.66, trin: 0.82 },
    breadthMomentum: { mcclellanOscillator: 46, breadthThrustRatio: 0.58, status: 'RECOVERING' },
    changeSummary: {
      advanceRatioChange: 0.09, ma20RatioChange: 0.12, totalAmountChangeRatio: 0.08,
      newHighLowBalanceChange: 64, mcclellanOscillatorChange: 38
    },
    history: [
      { businessDate: '2026-08-18', advanceRatio: 0.41, ma20Ratio: 0.53, ma60Ratio: 0.54, medianChangePct: -0.2, advanceAmountRatio: 0.43, mcclellanOscillator: -24 },
      { businessDate: '2026-08-19', advanceRatio: 0.18, ma20Ratio: 0.42, ma60Ratio: 0.48, medianChangePct: -2.1, advanceAmountRatio: 0.22, mcclellanOscillator: -78 },
      { businessDate: '2026-08-20', advanceRatio: 0.53, ma20Ratio: 0.49, ma60Ratio: 0.51, medianChangePct: 0.35, advanceAmountRatio: 0.54, mcclellanOscillator: 8 },
      { businessDate: '2026-08-21', advanceRatio: 0.64, ma20Ratio: 0.61, ma60Ratio: 0.55, medianChangePct: 0.82, advanceAmountRatio: 0.66, mcclellanOscillator: 46 }
    ]
  },
  sectors: [
    { sectorCode: 'BK1040', sectorName: '创新药', return1d: 2.1, return5d: 5.8, mainNetInflow: 3_200_000_000, breadthRatio: 0.72, crowdingScore: 63, rotationScore: 82, stage: 'PERSISTENT' },
    { sectorCode: 'BK0737', sectorName: '贵金属', return1d: 0.8, return5d: 2.2, mainNetInflow: 1_100_000_000, breadthRatio: 0.61, crowdingScore: 48, rotationScore: 66, stage: 'EMERGING' },
    { sectorCode: 'BK0917', sectorName: '半导体', return1d: -0.3, return5d: -2.1, mainNetInflow: -900_000_000, breadthRatio: 0.37, crowdingScore: 35, rotationScore: 38, stage: 'FADING' }
  ]
};

describe('market transition decision', () => {
  test('detects repair expansion and builds three next-session scenarios', () => {
    const decision = buildMarketTransitionDecision(repairingWorkspace);

    expect(decision.transition.code).toBe('REPAIR_EXPANSION');
    expect(decision.transition.strength).toBeGreaterThanOrEqual(60);
    expect(decision.scenarios).toHaveLength(3);
    expect(decision.scenarios.filter(item => item.emphasis === 'PRIMARY')).toHaveLength(1);
    expect(decision.trajectory[decision.trajectory.length - 1]?.businessDate).toBe('2026-08-21');
  });

  test('measures leadership, fragility and preferred research sectors without stock candidates', () => {
    const decision = buildMarketTransitionDecision(repairingWorkspace);

    expect(decision.gauges.map(item => item.code)).toEqual([
      'PARTICIPATION', 'BREADTH_MOMENTUM', 'LEADERSHIP_HEALTH', 'FRAGILITY'
    ]);
    expect(decision.discoveryContext.preferredSectors).toEqual(['创新药', '贵金属']);
    expect(decision.discoveryContext.avoidSectors).toContain('半导体');
    expect(decision.discoveryContext).not.toHaveProperty('candidates');
  });

  test('keeps missing inputs explicit instead of manufacturing strong scores', () => {
    const decision = buildMarketTransitionDecision({ businessDate: '2026-08-21', qualityStatus: 'PARTIAL' });

    expect(decision.transition.code).toBe('INSUFFICIENT_DATA');
    expect(decision.transition.strength).toBe(0);
    expect(decision.gauges.every(item => item.available === false)).toBe(true);
    expect(decision.scenarios[0].triggers).toContain('等待全A宽度与行业轮动恢复');
  });
});
