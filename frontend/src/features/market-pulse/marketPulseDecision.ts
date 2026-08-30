import type {
  MarketInternalHistoryPoint,
  MarketNextSessionScenario,
  MarketPulseHistoryPoint,
  MarketPulseWorkspace,
  MarketStateTrajectoryPoint,
  MarketTransitionCode,
  MarketTransitionDecision,
  MarketTransitionGauge,
  SectorRotation,
  StockDiscoveryMarketContext
} from './marketPulseTypes';

const transitionLabels: Record<MarketTransitionCode, string> = {
  REPAIR_EXPANSION: '修复正在扩散',
  NARROWING_DIVERGENCE: '强势正在变窄',
  RISK_RELEASE: '风险仍在释放',
  RAPID_ROTATION: '主线快速轮动',
  RANGE_BALANCE: '多空暂时平衡',
  INSUFFICIENT_DATA: '等待结构数据'
};

function clamp(value: number) {
  return Math.max(0, Math.min(100, Math.round(value)));
}

function finite(value?: number): value is number {
  return value != null && Number.isFinite(value);
}

function average(values: Array<number | undefined>) {
  const available = values.filter(finite);
  return available.length ? available.reduce((sum, value) => sum + value, 0) / available.length : undefined;
}

function ratioScore(value?: number) {
  return finite(value) ? clamp(value * 100) : undefined;
}

function participationScore(workspace: MarketPulseWorkspace) {
  const breadth = workspace.breadth;
  return average([
    ratioScore(breadth?.advanceRatio),
    ratioScore(breadth?.trendBreadth?.ma20Ratio),
    ratioScore(breadth?.trendBreadth?.ma60Ratio)
  ]);
}

function momentumScore(workspace: MarketPulseWorkspace) {
  const breadth = workspace.breadth;
  const oscillator = breadth?.breadthMomentum?.mcclellanOscillator;
  const oscillatorScore = finite(oscillator) ? clamp(50 + oscillator * 0.6) : undefined;
  return average([
    oscillatorScore,
    ratioScore(breadth?.breadthMomentum?.breadthThrustRatio),
    ratioScore(breadth?.volumePressure?.advanceAmountRatio)
  ]);
}

function leadershipScore(sectors: SectorRotation[]) {
  if (!sectors.length) {
    return undefined;
  }
  const broadParticipation = average(sectors.map(item => ratioScore(item.breadthRatio)));
  const positiveShare = sectors.filter(item => (item.return5d ?? item.return1d ?? 0) > 0).length / sectors.length * 100;
  const leaders = [...sectors].sort((left, right) => right.rotationScore - left.rotationScore).slice(0, 3);
  const leaderQuality = average(leaders.map(item => item.rotationScore));
  return average([broadParticipation, positiveShare, leaderQuality]);
}

function capitalConcentration(sectors: SectorRotation[]) {
  const positiveFlows = sectors.map(item => Math.max(0, item.mainNetInflow ?? 0)).filter(value => value > 0);
  const total = positiveFlows.reduce((sum, value) => sum + value, 0);
  if (!total) {
    return undefined;
  }
  const topFlow = Math.max(...positiveFlows);
  return clamp(topFlow / total * 100);
}

function fragilityScore(workspace: MarketPulseWorkspace, leadership?: number) {
  const sectors = workspace.sectors ?? [];
  if (!sectors.length && !finite(workspace.regime?.features?.sectorDispersion)) {
    return undefined;
  }
  const crowding = average(
    [...sectors].sort((left, right) => right.rotationScore - left.rotationScore).slice(0, 3).map(item => item.crowdingScore)
  );
  const dispersion = finite(workspace.regime?.features?.sectorDispersion)
    ? clamp((workspace.regime?.features?.sectorDispersion ?? 0) * 2_000)
    : undefined;
  const concentration = capitalConcentration(sectors);
  return average([crowding, dispersion, concentration, finite(leadership) ? 100 - leadership : undefined]);
}

function changeImpulse(workspace: MarketPulseWorkspace) {
  const change = workspace.breadth?.changeSummary;
  if (!change) {
    return undefined;
  }
  return average([
    finite(change.advanceRatioChange) ? clamp(change.advanceRatioChange * 1_000) : undefined,
    finite(change.ma20RatioChange) ? clamp(change.ma20RatioChange * 800) : undefined,
    finite(change.totalAmountChangeRatio) ? clamp(50 + change.totalAmountChangeRatio * 400) : undefined,
    finite(change.newHighLowBalanceChange) ? clamp(50 + change.newHighLowBalanceChange * 0.7) : undefined,
    finite(change.mcclellanOscillatorChange) ? clamp(50 + change.mcclellanOscillatorChange * 0.8) : undefined
  ]);
}

function gauge(code: MarketTransitionGauge['code'], label: string, score: number | undefined,
               positiveStatus: string, negativeStatus: string, detail: string, inverse = false): MarketTransitionGauge {
  const available = finite(score);
  const safeScore = available ? clamp(score) : 0;
  const isPositive = inverse ? safeScore < 55 : safeScore >= 55;
  return {
    code,
    label,
    score: safeScore,
    available,
    status: available ? (isPositive ? positiveStatus : negativeStatus) : '等待数据',
    detail: available ? detail : '当前输入不足，不生成替代结论'
  };
}

function transitionCode(workspace: MarketPulseWorkspace, participation?: number, momentum?: number,
                        leadership?: number, fragility?: number, impulse?: number): MarketTransitionCode {
  const hasMarketStructure = [participation, momentum, impulse].some(finite);
  const availableDimensions = [participation, momentum, leadership, fragility, impulse].filter(finite).length;
  if (!hasMarketStructure || availableDimensions < 2 || !(workspace.sectors?.length)) {
    return 'INSUFFICIENT_DATA';
  }
  const breadthSupport = momentum ?? leadership ?? 50;
  if (workspace.regime?.marketStage === 'SELL_OFF' || ((participation ?? 50) < 34 && breadthSupport < 42)) {
    return 'RISK_RELEASE';
  }
  if ((workspace.regime?.marketStage === 'POST_SELL_OFF_REPAIR' || (impulse ?? 0) >= 64)
      && (participation ?? 0) >= 50 && breadthSupport >= 52) {
    return 'REPAIR_EXPANSION';
  }
  if ((fragility ?? 0) >= 66 || ((participation ?? 50) < 48 && breadthSupport >= 55)) {
    return 'NARROWING_DIVERGENCE';
  }
  if (workspace.regime?.rotationState === 'FAST') {
    return 'RAPID_ROTATION';
  }
  return 'RANGE_BALANCE';
}

function transitionDrivers(workspace: MarketPulseWorkspace, code: MarketTransitionCode,
                           participation?: number, momentum?: number, fragility?: number) {
  if (code === 'INSUFFICIENT_DATA') {
    return ['等待全A宽度与行业轮动恢复'];
  }
  const values = [
    finite(participation) ? `市场参与度 ${clamp(participation)} / 100` : '',
    finite(momentum) ? `宽度动量 ${clamp(momentum)} / 100` : '',
    finite(fragility) ? `结构脆弱度 ${clamp(fragility)} / 100` : ''
  ].filter(Boolean);
  const change = workspace.breadth?.changeSummary;
  if ((change?.advanceRatioChange ?? 0) > 0.03) {
    values.push('上涨参与较上一交易日明显扩张');
  }
  if ((change?.ma20RatioChange ?? 0) < -0.03) {
    values.push('短期趋势宽度正在收缩');
  }
  return values.slice(0, 4);
}

function transitionSummary(code: MarketTransitionCode) {
  const summaries: Record<MarketTransitionCode, string> = {
    REPAIR_EXPANSION: '宽度、成交压力与家数动量同步改善，修复正从局部反弹走向更广参与。',
    NARROWING_DIVERGENCE: '表面强势仍在，但参与度与领导健康度没有同步跟上，主线更容易出现兑现。',
    RISK_RELEASE: '下跌参与和卖压仍占优势，当前重点是等待风险收敛，而不是抢先定义底部。',
    RAPID_ROTATION: '资金偏好切换快于趋势形成，适合等待回撤确认，不适合追逐单日最强方向。',
    RANGE_BALANCE: '多空结构暂未形成决定性倾斜，下一步取决于参与度与成交压力能否同向突破。',
    INSUFFICIENT_DATA: '尚未获得完整的全A宽度和行业截面，页面保留空白而不制造强弱判断。'
  };
  return summaries[code];
}

function trajectoryPoint(point: MarketInternalHistoryPoint): MarketStateTrajectoryPoint | undefined {
  if (!point.businessDate) {
    return undefined;
  }
  const participation = average([
    ratioScore(point.advanceRatio), ratioScore(point.ma20Ratio), ratioScore(point.ma60Ratio)
  ]);
  const appetite = average([
    finite(point.medianChangePct) ? clamp(50 + point.medianChangePct * 16) : undefined,
    ratioScore(point.advanceAmountRatio),
    finite(point.mcclellanOscillator) ? clamp(50 + point.mcclellanOscillator * 0.6) : undefined
  ]);
  if (!finite(participation) || !finite(appetite)) {
    return undefined;
  }
  const state = participation >= 55
    ? (appetite >= 55 ? 'EXPANSION' : 'ROTATION')
    : (appetite >= 50 ? 'REPAIR' : 'RISK_RELEASE');
  return { businessDate: point.businessDate, participation: clamp(participation), riskAppetite: clamp(appetite), state };
}

function historyTrajectoryPoint(point: MarketPulseHistoryPoint): MarketStateTrajectoryPoint | undefined {
  if (!point.businessDate || !finite(point.advanceRatio)) {
    return undefined;
  }
  const stageAppetite: Record<string, number> = {
    RISK_ON: 76, TREND_EXPANSION: 76, POST_SELL_OFF_REPAIR: 56,
    RANGE_ROTATION: 50, HIGH_LEVEL_DIVERGENCE: 42, SELL_OFF: 20
  };
  const riskAppetite = average([
    finite(point.medianChangePct) ? clamp(50 + point.medianChangePct * 16) : undefined,
    stageAppetite[point.marketStage ?? '']
  ]);
  if (!finite(riskAppetite)) {
    return undefined;
  }
  const participation = clamp(point.advanceRatio * 100);
  const state = participation >= 55
    ? (riskAppetite >= 55 ? 'EXPANSION' : 'ROTATION')
    : (riskAppetite >= 50 ? 'REPAIR' : 'RISK_RELEASE');
  return { businessDate: point.businessDate, participation, riskAppetite: clamp(riskAppetite), state };
}

function buildTrajectory(workspace: MarketPulseWorkspace) {
  const internalTrajectory = (workspace.breadth?.history ?? []).slice(-10).map(trajectoryPoint)
    .filter((point): point is MarketStateTrajectoryPoint => Boolean(point));
  if (internalTrajectory.length >= 2) {
    return internalTrajectory;
  }
  return [...(workspace.historyPoints ?? [])].sort((left, right) => (left.businessDate ?? '').localeCompare(right.businessDate ?? ''))
    .slice(-10).map(historyTrajectoryPoint)
    .filter((point): point is MarketStateTrajectoryPoint => Boolean(point));
}

function scenarios(code: MarketTransitionCode, participation?: number, momentum?: number,
                   fragility?: number): MarketNextSessionScenario[] {
  if (code === 'INSUFFICIENT_DATA') {
    return [
      { code: 'EXTEND_REPAIR', title: '等待结构恢复', matchScore: 0, emphasis: 'PRIMARY', triggers: ['等待全A宽度与行业轮动恢复'], posture: '暂不根据缺失数据改变研究姿态' },
      { code: 'ROTATE_AND_SPLIT', title: '观察轮动', matchScore: 0, emphasis: 'SECONDARY', triggers: ['等待行业参与率恢复'], posture: '保留观察列表' },
      { code: 'RISK_RELEASE', title: '风险防线', matchScore: 0, emphasis: 'GUARD', triggers: ['等待买卖压力恢复'], posture: '不扩大风险暴露' }
    ];
  }
  const repairScore = clamp(average([participation, momentum, finite(fragility) ? 100 - fragility : undefined]) ?? 0);
  const splitScore = clamp(average([fragility, finite(participation) ? 100 - Math.abs(participation - 50) : undefined]) ?? 0);
  const releaseScore = clamp(average([
    finite(participation) ? 100 - participation : undefined,
    finite(momentum) ? 100 - momentum : undefined,
    fragility
  ]) ?? 0);
  const scores = { EXTEND_REPAIR: repairScore, ROTATE_AND_SPLIT: splitScore, RISK_RELEASE: releaseScore };
  const primary = Object.entries(scores).sort((left, right) => right[1] - left[1])[0][0];
  return [
    {
      code: 'EXTEND_REPAIR', title: '修复继续扩散', matchScore: repairScore,
      emphasis: primary === 'EXTEND_REPAIR' ? 'PRIMARY' : 'SECONDARY',
      triggers: ['上涨比例保持在 55% 以上', '上涨成交额占比不回落至 50% 以下', 'McClellan 维持正值'],
      posture: '允许正常寻找机会，优先等待主线回撤确认'
    },
    {
      code: 'ROTATE_AND_SPLIT', title: '冲高后快速分化', matchScore: splitScore,
      emphasis: primary === 'ROTATE_AND_SPLIT' ? 'PRIMARY' : 'SECONDARY',
      triggers: ['领涨行业集中度继续上升', 'MA20 宽度停止改善', '强势行业动量先于相对强度回落'],
      posture: '减少追高，寻找主线内部低位与新接力方向'
    },
    {
      code: 'RISK_RELEASE', title: '卖压再次占优', matchScore: releaseScore,
      emphasis: primary === 'RISK_RELEASE' ? 'PRIMARY' : 'GUARD',
      triggers: ['上涨比例跌破 40%', '新低数量重新扩张', '上涨成交额占比跌破 45%'],
      posture: '收紧风险预算，等待宽度与卖压同时企稳'
    }
  ];
}

function discoveryContext(workspace: MarketPulseWorkspace, code: MarketTransitionCode,
                          fragility?: number): StockDiscoveryMarketContext {
  const sectors = [...(workspace.sectors ?? [])].sort((left, right) => right.rotationScore - left.rotationScore);
  const preferredSectors = sectors.filter(item => ['ACCELERATING', 'PERSISTENT', 'EMERGING'].includes(item.stage ?? '')
    && item.rotationScore >= 55 && (item.crowdingScore ?? 0) < 80).slice(0, 3).map(item => item.sectorName);
  const avoidSectors = sectors.filter(item => ['OVERHEATED', 'FADING', 'WEAK'].includes(item.stage ?? '')
    || (item.crowdingScore ?? 0) >= 80).slice(0, 3).map(item => item.sectorName);
  const riskPosture = code === 'RISK_RELEASE' || code === 'NARROWING_DIVERGENCE'
    ? 'DEFENSIVE'
    : code === 'REPAIR_EXPANSION' && (fragility ?? 100) < 58 ? 'OFFENSIVE' : 'BALANCED';
  const chasePolicy = riskPosture === 'DEFENSIVE'
    ? 'NO_CHASING'
    : workspace.regime?.rotationState === 'FAST' || (fragility ?? 0) >= 50 ? 'PULLBACK_ONLY' : 'CONFIRMATION_ALLOWED';
  return {
    businessDate: workspace.businessDate,
    transitionCode: code,
    transitionLabel: transitionLabels[code],
    riskPosture,
    preferredSectors,
    avoidSectors,
    chasePolicy,
    summary: transitionSummary(code)
  };
}

export function buildMarketTransitionDecision(workspace: MarketPulseWorkspace): MarketTransitionDecision {
  const participation = participationScore(workspace);
  const momentum = momentumScore(workspace);
  const leadership = leadershipScore(workspace.sectors ?? []);
  const fragility = fragilityScore(workspace, leadership);
  const impulse = changeImpulse(workspace);
  const code = transitionCode(workspace, participation, momentum, leadership, fragility, impulse);
  const strength = code === 'INSUFFICIENT_DATA'
    ? 0
    : clamp(average([impulse, participation, momentum, leadership, finite(fragility) ? 100 - fragility : undefined]) ?? 0);
  return {
    transition: {
      code,
      label: transitionLabels[code],
      strength,
      summary: transitionSummary(code),
      drivers: transitionDrivers(workspace, code, participation, momentum, fragility)
    },
    gauges: [
      gauge('PARTICIPATION', '市场参与度', participation, '参与扩散', '参与收缩', '综合上涨家数、MA20 与 MA60 趋势宽度'),
      gauge('BREADTH_MOMENTUM', '宽度动量', momentum, '动量改善', '动量偏弱', '综合 McClellan、参与率 EMA 与上涨成交额占比'),
      gauge('LEADERSHIP_HEALTH', '主线健康度', leadership, '内部健康', '少数领涨', '观察强势行业内部宽度、持续性与轮动得分'),
      gauge('FRAGILITY', '结构脆弱度', fragility, '结构稳定', '容易分化', '综合资金集中、行业离散与拥挤水平', true)
    ],
    trajectory: buildTrajectory(workspace),
    scenarios: scenarios(code, participation, momentum, fragility),
    discoveryContext: discoveryContext(workspace, code, fragility)
  };
}
