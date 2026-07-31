import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test, vi } from 'vitest';

import { KnowledgeTopicWorkspace } from '../knowledgeTypes';
import { VerificationQueue } from './VerificationQueue';

const workspace: KnowledgeTopicWorkspace = {
  topic: {
    id: 7,
    name: '先进封装供需可能进入上行周期',
    description: '持续检验产能利用率、价格和资本开支。',
    lifecycleStatus: 'ACTIVE',
    masteryStatus: 'REVIEWING',
    revision: 2,
    articleCount: 3
  },
  events: [{ id: 31, canonicalTitle: '公司发布季度经营更新', lastMeaningfulUpdateAt: '2026-07-31T08:00:00' }],
  evidence: [
    {
      id: 1,
      eventId: 31,
      sourceTier: 'MEDIA',
      evidenceType: 'FACT',
      claim: '公司披露二季度先进封装收入同比增长 28%。',
      confidence: 76,
      articleTitle: '季度经营数据报道',
      articleUrl: 'https://news.example.com/quarter'
    },
    {
      id: 2,
      eventId: 31,
      sourceTier: 'COMPANY',
      evidenceType: 'TIMELINE',
      claim: '公司公告新产线已完成设备搬入。',
      confidence: 91,
      articleTitle: '公司产线建设公告',
      articleUrl: 'https://investor.example-corp.com/releases/line'
    }
  ],
  tasks: [],
  entries: []
};

test('shows an honest empty queue instead of falling back to article events', () => {
  render(<VerificationQueue workspaces={[]} onNavigate={vi.fn()} />);

  expect(screen.getByRole('heading', { name: '当前没有需要核验的投资命题' })).toBeInTheDocument();
  expect(screen.getByText(/文章和事件不会自动进入这里/)).toBeInTheDocument();
});

test('defaults to unresolved propositions and explains which recognition they may change', () => {
  render(<VerificationQueue workspaces={[workspace]} onNavigate={vi.fn()} />);

  expect(screen.getByRole('button', { name: /公司披露二季度先进封装收入同比增长 28%/ })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /公司公告新产线已完成设备搬入/ })).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '公司披露二季度先进封装收入同比增长 28%。' })).toBeInTheDocument();
  expect(screen.getByText('影响的投资认识')).toBeInTheDocument();
  expect(screen.getAllByText('先进封装供需可能进入上行周期').length).toBeGreaterThan(0);
  expect(screen.getByText('需要找到公告、监管或公司一手材料')).toBeInTheDocument();
});

test('keeps first-party facts in a separate recorded view', async () => {
  render(<VerificationQueue workspaces={[workspace]} onNavigate={vi.fn()} />);

  await userEvent.click(screen.getByRole('button', { name: '已记录 1' }));

  expect(screen.getByRole('heading', { name: '公司公告新产线已完成设备搬入。' })).toBeInTheDocument();
  expect(screen.getByText('一手事实已记录，不占用待核验队列。')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '打开来源' })).toHaveAttribute('href', 'https://investor.example-corp.com/releases/line');
});

test('opens the affected recognition from the proposition dossier', async () => {
  const onNavigate = vi.fn();
  render(<VerificationQueue workspaces={[workspace]} onNavigate={onNavigate} />);

  await userEvent.click(screen.getByRole('button', { name: '查看认识档案' }));

  expect(onNavigate).toHaveBeenCalledWith('?section=topics&topic=7');
});
