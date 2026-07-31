import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

import { api } from '../../shared/api/client';
import { NewsView } from './NewsView';

vi.mock('../../shared/api/client', () => ({ api: vi.fn() }));

const event = {
  id: 10,
  title: '宁德时代发布新一代电池',
  summary: '两家来源确认新品正式发布。',
  categoryCode: 'COMPANY',
  priorityScore: 92,
  recommendation: '重点关注',
  reasons: ['与自选「宁德时代」直接相关', '已有多个独立来源交叉确认', '事件仍在快速更新'],
  watchlistRelated: true,
  watchlistExplanation: '与自选「宁德时代」直接相关',
  sourceCount: 3,
  signalCount: 4,
  uncertainty: '价格与量产节奏仍待公告确认',
  nextObservation: '观察公司公告和供应链反馈',
  suggestedResearchQuestion: '围绕“宁德时代发布新一代电池”，哪些事实已经确认，后续应重点观察什么？',
  lastSeenAt: '2026-07-31T15:55:00'
};

const liveItem = {
  id: 'CLS:1', kind: 'FLASH', title: '宁德时代发布新一代电池', content: '新品正式发布。',
  url: 'https://example.com/1', publishedAt: '2026-07-31T15:55:00', providerCode: 'CLS',
  sourceName: '财联社', sourceTier: 'TIER_1', categoryCode: 'COMPANY'
};

const snapshot = {
  overview: { eventCount: 1, highPriorityCount: 1, watchlistRelatedCount: 1, sourceCount: 3 },
  events: [event],
  liveItems: [liveItem],
  warnings: [],
  refreshedAt: '2026-07-31T16:00:00'
};

const detail = {
  event,
  signals: [
    { id: 1, title: event.title, content: '新品正式发布。', url: 'https://example.com/1', sourceName: '财联社', sourceTier: 'TIER_1', publishedAt: liveItem.publishedAt, relationType: 'PRIMARY', matchScore: 1, matchReason: '代表信号' },
    { id: 2, title: '宁德时代新电池正式发布', content: '发布会信息。', sourceName: '同花顺', sourceTier: 'TIER_1', publishedAt: liveItem.publishedAt, relationType: 'SUPPORTING', matchScore: 0.84, matchReason: '主体、动作和标题语义一致' }
  ]
};

const categories = [{ code: 'COMPANY', name: '公司动态', enabled: true, displayOrder: 10 }];

beforeEach(() => {
  vi.useRealTimers();
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path === '/api/research-radar/events/10') return Promise.resolve(detail);
    return Promise.resolve(snapshot);
  });
});

test('renders explainable priority cards before the live wire', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);

  const focus = await screen.findByRole('heading', { name: '今天值得关注' });
  expect(screen.getByText('与自选「宁德时代」直接相关')).toBeInTheDocument();
  expect(screen.getByText('92')).toBeInTheDocument();
  const board = screen.getByTestId('research-radar-board');
  expect(within(board).getAllByRole('heading')[0]).toBe(focus);
  expect(screen.getByRole('heading', { name: '实时发生' })).toBeInTheDocument();
});

test('loads original signals only when the user asks for evidence', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await screen.findByRole('heading', { name: event.title });

  await userEvent.click(screen.getByRole('button', { name: '查看依据' }));

  expect(await screen.findByText('3 个独立来源共同报道')).toBeInTheDocument();
  expect(screen.getByText('同花顺')).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/research-radar/events/10');
});

test('research action only hands the suggested question to the parent', async () => {
  const onResearch = vi.fn();
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={onResearch} />);
  await screen.findByRole('heading', { name: event.title });

  await userEvent.click(screen.getByRole('button', { name: '围绕此事研究' }));

  expect(onResearch).toHaveBeenCalledWith(event.suggestedResearchQuestion);
  expect(api).not.toHaveBeenCalledWith('/api/research/runs', expect.anything());
});

test('supports watchlist-only filtering and degraded snapshots', async () => {
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    return Promise.resolve({ ...snapshot, warnings: ['实时资讯暂不可用，已展示最近一次结果'] });
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await screen.findByRole('button', { name: '与我相关' });

  fireEvent.click(screen.getByRole('button', { name: '与我相关' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/research-radar?category=ALL&watchlistOnly=true&limit=20'));
  expect(screen.getByText('实时来源暂不可用，当前展示最近一次雷达结果')).toBeInTheDocument();
});

test('polling waits for confirmation before inserting a new live item', async () => {
  vi.useFakeTimers();
  let calls = 0;
  const updated = { ...snapshot, liveItems: [{ ...liveItem, id: 'THS:2', title: '新的实时消息' }, ...snapshot.liveItems] };
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    calls += 1; return Promise.resolve(calls === 1 ? snapshot : updated);
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await act(async () => { await Promise.resolve(); });

  await act(async () => { vi.advanceTimersByTime(45_000); await Promise.resolve(); });

  expect(screen.queryByText('新的实时消息')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '发现 1 条新资讯' }));
  expect(screen.getByText('新的实时消息')).toBeInTheDocument();
});
