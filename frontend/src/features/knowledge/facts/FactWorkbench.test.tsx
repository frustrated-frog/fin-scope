import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, test } from 'vitest';

import { EventCluster, EvidenceItem } from '../../../shared/types';
import { FactWorkbench } from './FactWorkbench';

const events: EventCluster[] = [
  {
    id: 1,
    canonicalTitle: '公司确认第二季度海外收入同比增长 38%',
    themeCode: 'COMPANY',
    summary: '公司半年报披露海外业务保持较快增长。',
    lastMeaningfulUpdateAt: '2026-07-31T09:00:00'
  },
  {
    id: 2,
    canonicalTitle: '行业需求可能在下半年回暖',
    themeCode: 'INDUSTRY',
    summary: '多家媒体援引渠道观点判断需求改善。',
    lastMeaningfulUpdateAt: '2026-07-30T09:00:00'
  }
];

const evidence: EvidenceItem[] = [
  {
    id: 11,
    eventId: 1,
    sourceTier: 'COMPANY',
    evidenceType: 'FACT',
    claim: '公司半年报披露海外收入同比增长 38%。',
    confidence: 92,
    articleTitle: '2026 年半年度报告',
    articleUrl: 'https://example.com/report'
  },
  {
    id: 21,
    eventId: 2,
    sourceTier: 'MEDIA',
    evidenceType: 'IMPACT',
    claim: '渠道反馈显示下半年需求可能改善。',
    confidence: 61,
    articleTitle: '行业渠道跟踪'
  }
];

test('presents a fact index and a focused evidence dossier in one workspace', async () => {
  render(<FactWorkbench events={events} evidenceItems={evidence} />);

  expect(screen.getByRole('heading', { name: '把新闻核成事实，再决定是否更新判断' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: new RegExp(events[0].canonicalTitle) }));
  expect(screen.getAllByText('证据较充分')).toHaveLength(2);
  expect(screen.getByRole('heading', { name: events[0].canonicalTitle })).toBeInTheDocument();
  expect(screen.getByText('公司经营')).toBeInTheDocument();
  expect(screen.queryByText('COMPANY')).not.toBeInTheDocument();
  expect(screen.getByText('公司半年报披露海外收入同比增长 38%。')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: '打开原文' })).toHaveAttribute('href', 'https://example.com/report');
});

test('filters the index and keeps the selected dossier useful', async () => {
  render(<FactWorkbench events={events} evidenceItems={evidence} />);

  await userEvent.click(screen.getByRole('button', { name: '待核验 1' }));
  expect(screen.queryByRole('button', { name: new RegExp(events[0].canonicalTitle) })).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: events[1].canonicalTitle })).toBeInTheDocument();

  await userEvent.clear(screen.getByRole('searchbox', { name: '搜索事实' }));
  await userEvent.type(screen.getByRole('searchbox', { name: '搜索事实' }), '不存在的事实');
  expect(screen.getByText('没有符合条件的事实候选')).toBeInTheDocument();
});

test('explains the empty state without inventing facts', () => {
  render(<FactWorkbench events={[]} evidenceItems={[]} />);

  expect(screen.getByText('还没有可核验的事实候选')).toBeInTheDocument();
  expect(screen.getByText('这里不会创建或修改数据库记录。')).toBeInTheDocument();
});

test('keeps material-free events out of the working queue until explicitly requested', async () => {
  const emptyEvent: EventCluster = {
    id: 3,
    canonicalTitle: '尚未获得任何材料的事件',
    themeCode: 'OTHER'
  };
  render(<FactWorkbench events={[...events, emptyEvent]} evidenceItems={evidence} />);

  expect(screen.queryByRole('button', { name: new RegExp(emptyEvent.canonicalTitle) })).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '显示无材料候选 1' }));
  expect(screen.getByRole('button', { name: new RegExp(emptyEvent.canonicalTitle) })).toBeInTheDocument();
});
