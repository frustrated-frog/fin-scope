import { render, screen, within } from '@testing-library/react';
import { expect, test } from 'vitest';

import { ResearchMissionView } from '../../shared/types';
import { ResearchMissionMap } from './ResearchMissionMap';

test('shows the active research task, real gap and deterministic fallback mode', () => {
  render(<ResearchMissionMap mission={runningMission()} />);

  const map = screen.getByRole('region', { name: '研究作战图' });
  expect(within(map).getAllByText('反方证据搜索').length).toBeGreaterThan(0);
  expect(within(map).getAllByText('正在取证').length).toBeGreaterThan(0);
  expect(within(map).getByText(/缺少反向或风险证据/)).toBeInTheDocument();
  expect(within(map).getByText('规则计划')).toBeInTheDocument();
  expect(within(map).getByText('4 / 6')).toBeInTheDocument();
  expect(within(map).getByText('2 / 2')).toBeInTheDocument();
});

test('renders completed task graph with explicit textual status and semantic order', () => {
  const mission = runningMission();
  mission.mission.status = 'COMPLETED';
  mission.mission.activeTaskKey = undefined;
  mission.tasks = mission.tasks.map((task) => ({ ...task, status: 'COMPLETED' }));

  render(<ResearchMissionMap mission={mission} />);

  expect(screen.getByText('研究已收束')).toBeInTheDocument();
  expect(screen.getAllByText('已完成').length).toBeGreaterThan(1);
  expect(screen.getAllByTestId('mission-task').map((node) => node.textContent)).toEqual(
    expect.arrayContaining([
      expect.stringContaining('基线来源扫描'),
      expect.stringContaining('支持证据搜索'),
      expect.stringContaining('反方证据搜索'),
      expect.stringContaining('证据判断'),
      expect.stringContaining('报告合成')
    ])
  );
});

test('does not invent a mission for a legacy run', () => {
  const { container } = render(<ResearchMissionMap mission={undefined} />);

  expect(container).toBeEmptyDOMElement();
  expect(screen.queryByRole('region', { name: '研究作战图' })).not.toBeInTheDocument();
});

test('does not present a pending plan as deterministic fallback', () => {
  const mission = runningMission();
  mission.mission.status = 'PLANNING';
  mission.mission.planningMode = 'PENDING';

  render(<ResearchMissionMap mission={mission} />);

  expect(screen.getByText('等待计划')).toBeInTheDocument();
  expect(screen.queryByText('规则计划')).not.toBeInTheDocument();
});

function runningMission(): ResearchMissionView {
  return {
    mission: {
      researchRunId: 21,
      goal: '验证AI资本开支能否持续',
      subject: 'AI算力',
      scopeSummary: '聚焦需求、供给、兑现与反方风险',
      successCriteria: ['至少六条有效证据', '至少两个独立来源', '同时包含支持与反方证据'],
      status: 'RUNNING',
      planningMode: 'DETERMINISTIC',
      planVersion: 1,
      maxActions: 12,
      activeTaskKey: 'search_counter',
      fallbackReason: 'MODEL_DISABLED'
    },
    tasks: [
      task('baseline_scan', '基线来源扫描', 'BASELINE', 'source_scan', 'COMPLETED'),
      task('search_support', '支持证据搜索', 'SUPPORT', 'public_news_search', 'COMPLETED'),
      task('search_counter', '反方证据搜索', 'COUNTER', 'public_news_search', 'RUNNING'),
      task('assess_evidence', '证据判断', 'ASSESS', 'evidence_assess', 'PENDING'),
      task('synthesize_report', '报告合成', 'SYNTHESIS', 'report_synthesis', 'PENDING')
    ],
    gaps: [{
      id: 2,
      researchRunId: 21,
      assessmentIndex: 2,
      afterTaskKey: 'search_support',
      sufficient: false,
      evidenceCount: 4,
      sourceCount: 2,
      supportCount: 4,
      counterCount: 0,
      warnings: ['有效证据数量不足 6 条', '缺少反向或风险证据，结论可能存在单边偏差'],
      recommendedIntent: 'COUNTER',
      stateHash: 'a'.repeat(64)
    }],
    tools: [
      {
        code: 'public_news_search',
        name: '公开新闻搜索',
        description: '根据已校验查询补充公开新闻证据',
        inputSchema: { queryText: '搜索词' },
        outputSchema: { articleIds: '文章ID' },
        timeoutMs: 45000,
        readOnly: false,
        parallelizable: false,
        riskLevel: 'MEDIUM',
        budgetType: 'EXTERNAL_ACTION'
      }
    ]
  };
}

function task(
  taskKey: string,
  title: string,
  intent: string,
  toolCode: string,
  status: string
) {
  return {
    id: 1,
    researchRunId: 21,
    taskKey,
    title,
    question: `${title}要回答什么？`,
    taskType: intent === 'ASSESS' ? 'ASSESS' : intent === 'SYNTHESIS' ? 'SYNTHESIS' : 'SEARCH',
    toolCode,
    intent,
    status,
    dependencies: [],
    evidenceDelta: status === 'COMPLETED' ? 2 : 0,
    sourceDelta: status === 'COMPLETED' ? 1 : 0
  };
}
