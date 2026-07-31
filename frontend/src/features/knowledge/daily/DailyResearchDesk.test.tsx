import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { KnowledgeOverview } from '../knowledgeTypes';
import { ResearchRadarSnapshot } from '../../news/researchRadarTypes';
import { DailyResearchDesk } from './DailyResearchDesk';

const overview: KnowledgeOverview = {
  acceptedTaskCount: 1,
  suggestedTaskCount: 2,
  dueReviewCount: 1,
  activeTopicCount: 1,
  actions: [{ type: 'CHECK_NEW_EVIDENCE', title: '复查需求判断', reason: '出现了新的经营数据', routeTarget: '?section=review&topic=3', topicId: 3 }],
  activeTopics: [],
  recentEntries: []
};

const radar: ResearchRadarSnapshot = {
  overview: { eventCount: 1, highPriorityCount: 1, watchlistRelatedCount: 1, sourceCount: 3 },
  events: [{
    id: 8,
    title: '储能系统报价出现变化',
    summary: '多条快讯指向近期报价变化。',
    priorityScore: 86,
    recommendation: '重点关注',
    reasons: ['多个来源共同报道'],
    watchlistRelated: true,
    watchlistExplanation: '与自选公司相关',
    sourceCount: 3,
    signalCount: 4,
    uncertainty: '报价能否持续仍需确认',
    nextObservation: '跟踪公司订单与季度毛利率',
    suggestedResearchQuestion: '报价变化是否可持续？',
    lastSeenAt: '2026-08-01T10:30:00'
  }],
  liveItems: [{ id: 'flash-1', kind: 'FLASH', title: '某公司披露新订单', content: '订单金额同比增长。', publishedAt: '2026-08-01T10:35:00', providerCode: 'CLS', sourceName: '财联社', sourceTier: 'MEDIA' }],
  warnings: [],
  refreshedAt: '2026-08-01T10:36:00'
};

test('turns daily news into research changes without calling flashes knowledge', async () => {
  const onNavigate = vi.fn();
  render(<DailyResearchDesk overview={overview} radar={radar} onNavigate={onNavigate} />);

  expect(screen.getByRole('heading', { name: '今天哪些变化，值得修正我的判断？' })).toBeInTheDocument();
  const changes = screen.getByRole('region', { name: '今日市场变化' });
  expect(within(changes).getByText('储能系统报价出现变化')).toBeInTheDocument();
  expect(within(changes).getByText('尚待确认')).toBeInTheDocument();
  expect(within(changes).getByText('报价能否持续仍需确认；尚未核对一手材料')).toBeInTheDocument();
  expect(within(changes).getByText('下一观察')).toBeInTheDocument();
  expect(screen.getByRole('complementary', { name: '今日快讯流水' })).toHaveTextContent('某公司披露新订单');
  expect(screen.queryByText('快讯知识')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '复查：复查需求判断' }));
  expect(onNavigate).toHaveBeenCalledWith('?section=review&topic=3');
});

test('keeps the recognition desk available when the news feed is degraded', () => {
  render(<DailyResearchDesk overview={overview} radar={null} radarError="今日资讯暂不可用" onNavigate={vi.fn()} />);

  expect(screen.getByRole('status')).toHaveTextContent('今日资讯暂不可用');
  expect(screen.getByRole('heading', { name: '需要更新的认识' })).toBeInTheDocument();
  expect(screen.getByText('复查需求判断')).toBeInTheDocument();
});

test('keeps article-derived learning drafts out of investment recognition updates', () => {
  render(<DailyResearchDesk overview={{
    ...overview,
    acceptedTaskCount: 2,
    actions: [{ type: 'CONTINUE_TASK', title: '某篇文章背后的变量是什么？', reason: '继续学习', routeTarget: '?section=learning&task=9', taskId: 9 }]
  }} radar={radar} onNavigate={vi.fn()} />);

  expect(screen.queryByText('某篇文章背后的变量是什么？')).not.toBeInTheDocument();
  expect(screen.getByText('2 个学习草稿尚未计入投资认识')).toBeInTheDocument();
  expect(screen.getByText('当前没有必须更新的判断')).toBeInTheDocument();
});
