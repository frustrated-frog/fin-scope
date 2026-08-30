import { fireEvent, render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';

import { MarketTransitionPanel } from './MarketTransitionPanel';
import type { MarketTransitionDecision } from './marketPulseTypes';

const decision: MarketTransitionDecision = {
  transition: {
    code: 'REPAIR_EXPANSION', label: '修复正在扩散', strength: 74,
    summary: '宽度、成交压力与家数动量同步改善，修复正从局部反弹走向更广参与。',
    drivers: ['市场参与度 60 / 100', '宽度动量 68 / 100', '上涨参与较上一交易日明显扩张']
  },
  gauges: [
    { code: 'PARTICIPATION', label: '市场参与度', score: 60, available: true, status: '参与扩散', detail: '综合上涨家数、MA20 与 MA60 趋势宽度' },
    { code: 'BREADTH_MOMENTUM', label: '宽度动量', score: 68, available: true, status: '动量改善', detail: '综合 McClellan、参与率 EMA 与上涨成交额占比' },
    { code: 'LEADERSHIP_HEALTH', label: '主线健康度', score: 66, available: true, status: '内部健康', detail: '观察强势行业内部宽度、持续性与轮动得分' },
    { code: 'FRAGILITY', label: '结构脆弱度', score: 48, available: true, status: '结构稳定', detail: '综合资金集中、行业离散与拥挤水平' }
  ],
  trajectory: [
    { businessDate: '2026-08-19', participation: 36, riskAppetite: 22, state: 'RISK_RELEASE' },
    { businessDate: '2026-08-20', participation: 51, riskAppetite: 54, state: 'REPAIR' },
    { businessDate: '2026-08-21', participation: 60, riskAppetite: 66, state: 'EXPANSION' }
  ],
  scenarios: [
    { code: 'EXTEND_REPAIR', title: '修复继续扩散', matchScore: 72, emphasis: 'PRIMARY', triggers: ['上涨比例保持在 55% 以上'], posture: '允许正常寻找机会，优先等待主线回撤确认' },
    { code: 'ROTATE_AND_SPLIT', title: '冲高后快速分化', matchScore: 53, emphasis: 'SECONDARY', triggers: ['领涨行业集中度继续上升'], posture: '减少追高，寻找主线内部低位与新接力方向' },
    { code: 'RISK_RELEASE', title: '卖压再次占优', matchScore: 39, emphasis: 'GUARD', triggers: ['上涨比例跌破 40%'], posture: '收紧风险预算，等待宽度与卖压同时企稳' }
  ],
  discoveryContext: {
    businessDate: '2026-08-21', transitionCode: 'REPAIR_EXPANSION', transitionLabel: '修复正在扩散',
    riskPosture: 'BALANCED', preferredSectors: ['创新药', '贵金属'], avoidSectors: ['半导体'],
    chasePolicy: 'PULLBACK_ONLY', summary: '宽度与动量同步改善'
  }
};

test('renders the transition thesis, state trajectory and three next-session scenarios', () => {
  render(<MarketTransitionPanel decision={decision} />);

  expect(screen.getByRole('heading', { name: '市场转折雷达' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '修复正在扩散' })).toBeInTheDocument();
  expect(screen.getByText('转折强度')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: '十日市场状态迁移轨迹' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '下一交易日情景' })).toBeInTheDocument();
  expect(screen.getAllByTestId('market-scenario')).toHaveLength(3);
  expect(screen.getByText('当前主路径')).toBeInTheDocument();
});

test('hands the market context to stock discovery without rendering stock candidates', () => {
  const onOpenStockDiscovery = vi.fn();
  render(<MarketTransitionPanel decision={decision} onOpenStockDiscovery={onOpenStockDiscovery} />);

  expect(screen.getByText('创新药 · 贵金属')).toBeInTheDocument();
  expect(screen.getByText('半导体')).toBeInTheDocument();
  expect(screen.queryByText('个股候选')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '带着当前环境进入股票发现' }));
  expect(onOpenStockDiscovery).toHaveBeenCalledWith(decision.discoveryContext);
});
