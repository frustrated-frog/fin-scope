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

  test('keeps a partial decision and trajectory when long-window breadth momentum is absent', () => {
    const decision = buildMarketTransitionDecision({
      businessDate: '2026-08-28', qualityStatus: 'READY',
      regime: { marketStage: 'RANGE_ROTATION', rotationState: 'SLOW', features: { sectorDispersion: 0.018 } },
      breadth: { advanceRatio: 0.54 },
      historyPoints: [
        { businessDate: '2026-08-27', marketStage: 'SELL_OFF', advanceRatio: 0.31, medianChangePct: -1.2 },
        { businessDate: '2026-08-28', marketStage: 'RANGE_ROTATION', advanceRatio: 0.54, medianChangePct: 0.22 }
      ],
      sectors: [
        { sectorCode: 'BK1', sectorName: '煤炭', return5d: 7, breadthRatio: 0.68, rotationScore: 74, stage: 'PERSISTENT' },
        { sectorCode: 'BK2', sectorName: '证券', return5d: 4, breadthRatio: 0.59, rotationScore: 64, stage: 'EMERGING' }
      ]
    });

    expect(decision.transition.code).toBe('RANGE_BALANCE');
    expect(decision.transition.strength).toBeGreaterThan(0);
    expect(decision.gauges.find(item => item.code === 'BREADTH_MOMENTUM')?.available).toBe(false);
    expect(decision.trajectory).toHaveLength(2);
    expect(decision.trajectory[1].state).toBe('REPAIR');
  });
});


test('uses bounded assessment values in the existing gauges and scenario calculations', () => {
  const structure = [
    { dimension: 'EXPANSION', label: '扩散程度', baselineScore: 60, adjustment: -8, finalScore: 52, scope: '同口径样本', components: {}, factIds: [] },
    { dimension: 'REPAIR_QUALITY', label: '修复质量', baselineScore: 60, adjustment: -10, finalScore: 50, scope: '同口径样本', components: {}, factIds: [] },
    { dimension: 'LEADERSHIP', label: '主线持续性', baselineScore: null, finalScore: null, scope: '样本不足', components: {}, factIds: [] },
    { dimension: 'FRAGILITY', label: '脆弱性', baselineScore: 60, adjustment: 5, finalScore: 65, scope: '同口径样本', components: {}, factIds: [] },
    { dimension: 'TURN_CONFIRMATION', label: '转折证据', baselineScore: 60, adjustment: -10, finalScore: 50, scope: '同口径样本', components: {}, factIds: [] }
  ];
  const reading = { businessDate: repairingWorkspace.businessDate!, status: 'READY', facts: [], sectorNames: {}, structure };
  const ai = buildMarketTransitionDecision(repairingWorkspace, reading);
  expect(ai.gauges.map(item => item.score)).toEqual([52, 50, 0, 65]);
  expect(ai.gauges[2].available).toBe(false);
  expect(ai.transition.strength).toBe(50);
  expect(ai.scenarios).toHaveLength(3);
  expect(ai.scenarios[0].matchScore).toBe(46);
  expect(ai.gauges[0].detail).toContain('AI 调整 -8');
  const disabled = buildMarketTransitionDecision(repairingWorkspace, { ...reading, status: 'DISABLED' });
  expect(disabled.gauges[0].score).toBe(60);
  expect(disabled.gauges[0].detail).toContain('使用规则评分');
  expect(disabled.transition.strength).toBe(60);
  const late = buildMarketTransitionDecision(repairingWorkspace, { ...reading, businessDate: '2026-08-20' });
  expect(late).toEqual(buildMarketTransitionDecision(repairingWorkspace));
});
