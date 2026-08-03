import { render, screen, within } from '@testing-library/react';
import { expect, test } from 'vitest';

import { ResearchAgentDecisionFlow } from './ResearchAgentDecisionFlow';

const trace = {
  state: {
    researchRunId: 15,
    status: 'RUNNING',
    stateVersion: 6,
    currentSubgoal: '补齐反方证据并验证需求回落风险',
    planSummary: '先检索风险证据，再重新评估证据缺口',
    memorySummary: '已有三条支持证据，反方证据仍不足',
    evidenceSummary: '3 条证据 / 2 个来源',
    attemptedFingerprints: ['fp-1'],
    decisionCount: 3,
    replanCount: 1,
    noProgressCount: 1,
    finishRejectionCount: 1,
    fallbackCount: 1
  },
  decisions: [
    {
      id: 101, researchRunId: 15, iteration: 1, decisionType: 'TOOL_CALL',
      currentSubgoal: '寻找需求回落风险', toolCode: 'public_news_search',
      targetGap: '缺少反方证据', expectedObservation: '获得至少一条需求转弱证据',
      decisionSummary: '主动搜索与主命题相反的公开信号', confidence: 0.78,
      decisionMode: 'DETERMINISTIC', actionFingerprint: 'fp-1', status: 'COMPLETED'
    },
    {
      id: 102, researchRunId: 15, iteration: 2, decisionType: 'PLAN_PATCH',
      currentSubgoal: '更换反方取证角度', targetGap: '原查询未产生新信息',
      expectedObservation: '从库存和订单角度获得反证', decisionSummary: '局部调整后续搜索计划',
      confidence: 0.71, decisionMode: 'MODEL', status: 'COMPLETED'
    },
    {
      id: 103, researchRunId: 15, iteration: 3, decisionType: 'FINISH',
      currentSubgoal: '校验是否可收束', decisionSummary: '尝试结束研究',
      confidence: 0.82, decisionMode: 'MODEL', status: 'REJECTED',
      validationError: '仍缺少可引用的反方证据'
    },
    {
      id: 104, researchRunId: 15, iteration: 4, decisionType: 'TOOL_CALL',
      currentSubgoal: '规则接管下一步', toolCode: 'public_news_search',
      decisionSummary: '模型超时后执行安全的规则决策', confidence: 0.66,
      decisionMode: 'DETERMINISTIC', status: 'COMPLETED',
      validationError: 'MODEL_TIMEOUT：模型决策响应超时，已切换规则决策'
    }
  ],
  observations: [{
    id: 201, researchRunId: 15, decisionId: 101, toolCode: 'public_news_search',
    status: 'NO_PROGRESS', observationSummary: '查询结果与已有证据重复',
    newInformation: '没有形成新的可引用事实', evidenceDelta: 0, sourceDelta: 0,
    dataRefs: [], retryable: true, attemptCount: 2, stateHash: 'state-1'
  }],
  trajectoryMetrics: {
    decisionCount: 3, observationCount: 1, decisionValidityRate: 1,
    observationFollowupRate: 1, duplicateActionRate: 0, noProgressRate: 1,
    replanSuccessRate: 0, finishFirstPassRate: 0, fallbackRate: 1 / 3, qualityScore: 53
  }
};

test('renders the live objective, budget and paired decision observations', () => {
  render(<ResearchAgentDecisionFlow agentCore={trace} remainingActions={7} planVersion={2} />);

  expect(screen.getByRole('region', { name: 'Agent 决策流' })).toBeInTheDocument();
  expect(screen.getByText('补齐反方证据并验证需求回落风险')).toBeInTheDocument();
  expect(screen.getByText('7 次')).toBeInTheDocument();
  expect(screen.getByText('剩余搜索')).toBeInTheDocument();
  expect(screen.getAllByText('多源公开资料搜索').length).toBeGreaterThan(0);
  expect(screen.getByText('V2')).toBeInTheDocument();
  expect(screen.getByText('证据健康')).toBeInTheDocument();
  expect(screen.getByText('当前缺口')).toBeInTheDocument();
  expect(screen.getByText('收敛状态')).toBeInTheDocument();
  expect(screen.getByText('1 次自动重试')).toBeInTheDocument();
  expect(screen.getByText('1 次异常降级')).toBeInTheDocument();
  expect(screen.queryByText('已有三条支持证据，反方证据仍不足')).not.toBeInTheDocument();

  const firstDecision = screen.getByTestId('agent-decision-101');
  expect(within(firstDecision).getByText('主动搜索与主命题相反的公开信号')).toBeInTheDocument();
  expect(within(firstDecision).getByText('查询结果与已有证据重复')).toBeInTheDocument();
  expect(within(firstDecision).getByText('规则降级')).toBeInTheDocument();
  expect(within(firstDecision).getByText('无新增')).toBeInTheDocument();
});

test('makes plan patches and rejected finish decisions explicit', () => {
  render(<ResearchAgentDecisionFlow agentCore={trace} remainingActions={7} planVersion={2} />);

  expect(screen.getByText('局部重规划')).toBeInTheDocument();
  expect(screen.getByText('完成校验未通过')).toBeInTheDocument();
  expect(screen.getByText('仍缺少可引用的反方证据')).toBeInTheDocument();
  expect(screen.getByText('轨迹质量 53')).toBeInTheDocument();
});

test('presents model timeout fallback separately from decision rejection', () => {
  render(<ResearchAgentDecisionFlow agentCore={trace} remainingActions={7} planVersion={2} />);

  const fallback = screen.getByText('MODEL_TIMEOUT：模型决策响应超时，已切换规则决策');
  expect(fallback).toHaveClass('research-agent-fallback-detail');
  expect(fallback).not.toHaveClass('research-agent-validation');
});

test('presents unavailable model assistance as a controlled status instead of rejection', () => {
  const assistedTrace = {
    ...trace,
    decisions: [{
      ...trace.decisions[0],
      decisionMode: 'CONTROLLED' as const,
      validationError: 'MODEL_ASSISTANCE_UNAVAILABLE：模型辅助未采用（TIMEOUT），本轮继续使用服务端受控决策'
    }]
  };

  render(<ResearchAgentDecisionFlow agentCore={assistedTrace} remainingActions={7} planVersion={2} />);

  const status = screen.getByText(/MODEL_ASSISTANCE_UNAVAILABLE/);
  expect(screen.getByText('受控编排')).toBeInTheDocument();
  expect(status).toHaveClass('research-agent-fallback-detail');
  expect(status).not.toHaveClass('research-agent-validation');
});

test('distinguishes an exhausted retry from a generic tool failure', () => {
  const failedTrace = {
    ...trace,
    observations: [{
      ...trace.observations[0],
      status: 'RETRYABLE_ERROR',
      observationSummary: '自动重试 1 次后仍失败：搜索上游超时',
      errorType: 'SEARCH_TIMEOUT',
      retryable: true,
      attemptCount: 2
    }]
  };

  render(<ResearchAgentDecisionFlow agentCore={failedTrace} remainingActions={7} planVersion={2} />);

  expect(screen.getByText('重试未恢复')).toBeInTheDocument();
  expect(screen.getByText('错误类型：SEARCH_TIMEOUT · 已完成自动重试')).toBeInTheDocument();
});

test('renders a rejected tool decision as terminal instead of waiting for an observation', () => {
  const rejectedTrace = {
    ...trace,
    decisions: [{
      ...trace.decisions[0],
      id: 105,
      iteration: 5,
      status: 'REJECTED',
      validationError: 'SEARCH_BUDGET_EXHAUSTED'
    }],
    observations: []
  };

  render(<ResearchAgentDecisionFlow agentCore={rejectedTrace} remainingActions={0} planVersion={2} />);

  expect(screen.getByText('工具调用已终止')).toBeInTheDocument();
  expect(screen.getByText('该决策未执行，因此不会返回 Observation。')).toBeInTheDocument();
  expect(screen.queryByText('等待工具返回 Observation…')).not.toBeInTheDocument();
});
