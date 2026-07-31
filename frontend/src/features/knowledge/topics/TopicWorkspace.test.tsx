import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { TopicWorkspace } from './TopicWorkspace';
import { KnowledgeTopicWorkspace } from '../knowledgeTypes';

const workspace: KnowledgeTopicWorkspace = {
  topic: { id: 2, name: 'Agent 工程化', description: '可靠工作流', lifecycleStatus: 'ACTIVE', masteryStatus: 'BUILDING', revision: 2 },
  reviewState: { topicId: 2, intervalDays: 14, reviewCount: 2, revision: 3, nextReviewAt: '2026-07-13T10:00:00' },
  events: [{ id: 9, canonicalTitle: 'Agent 回放框架发布', summary: '新增故障恢复回放', importanceScore: 82 }],
  evidence: [{ id: 11, eventId: 9, claim: '连续 30 天回放无状态偏差', sourceTier: 'PRIMARY', confidence: 88 }],
  tasks: [{ id: 7, topicId: 2, question: '如何验证可靠性？', status: 'TODO', revision: 1 }],
  entries: [{ id: 21, topicId: 2, entryType: 'CONCLUSION', entryStatus: 'FINAL', contentMarkdown: '可靠性来自可恢复执行。', confidence: 'HIGH', revision: 1 }]
};

test('renders a real evidence-to-judgment thread without inventing steps', () => {
  render(<TopicWorkspace workspace={workspace} onBack={vi.fn()} onReview={vi.fn()} />);
  expect(screen.getByText('当前判断')).toBeInTheDocument();
  expect(screen.getByText('来源事件')).toBeInTheDocument();
  expect(screen.getByText('代表证据')).toBeInTheDocument();
  expect(screen.getByText('待回答问题')).toBeInTheDocument();
  expect(screen.getByText('我的回答')).toBeInTheDocument();
  expect(screen.getByText('资料来源')).toBeInTheDocument();
});

test('compares the current conclusion with evidence and schedules next review', async () => {
  const onReview = vi.fn(async () => undefined);
  render(<TopicWorkspace workspace={workspace} reviewMode onBack={vi.fn()} onReview={onReview} />);
  expect(screen.getAllByText('可靠性来自可恢复执行。').length).toBeGreaterThan(0);
  expect(screen.getAllByText('连续 30 天回放无状态偏差').length).toBeGreaterThan(0);
  await userEvent.type(screen.getByLabelText('更新后的结论'), '结论仍成立，需要补充恢复耗时指标。');
  await userEvent.selectOptions(screen.getByLabelText('下次复习'), '30');
  await userEvent.click(screen.getByRole('button', { name: '完成复习' }));
  expect(onReview).toHaveBeenCalledWith(expect.objectContaining({ intervalDays: 30, expectedRevision: 3 }));
});

test('does not present an article-only file as established investment recognition', () => {
  render(<TopicWorkspace workspace={{
    topic: { id: 4, name: '一篇新闻的内容摘要', lifecycleStatus: 'ACTIVE', masteryStatus: 'EXPLORING', revision: 1, articleCount: 1 },
    events: [], evidence: [], tasks: [], entries: []
  }} onBack={vi.fn()} onReview={vi.fn()} />);

  expect(screen.getByText('待提炼材料')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '尚未形成投资认识' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '当前判断' })).not.toBeInTheDocument();
});
