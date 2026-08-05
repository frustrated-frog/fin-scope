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
  evidenceStatus: 'SUCCESS',
  evidenceSummary: '已补充2条证据，来自2个来源',
  evidenceWarning: '',
  evidenceCount: 2,
  evidenceSourceCount: 2,
  changeType: 'MULTI_SOURCE',
  changeSummary: '新增独立来源确认同一事件',
  interpretationStatus: 'SUCCESS',
  read: false,
  followed: false,
  disposition: 'ACTIVE',
  observationCount: 1,
  openObservationCount: 1,
  researchRunCount: 1,
  unreadNotificationCount: 0,
  suggestedResearchQuestion: '围绕“宁德时代发布新一代电池”，哪些事实已经确认，后续应重点观察什么？',
  lastSeenAt: '2026-07-31T15:55:00'
};

const liveItem = {
  id: 'CLS:1', kind: 'FLASH', title: '宁德时代发布新一代电池', content: '新品正式发布。',
  url: 'https://example.com/1', publishedAt: '2026-07-31T15:55:00', providerCode: 'CLS',
  sourceName: '财联社', sourceTier: 'TIER_1', categoryCode: 'COMPANY', categoryName: '公司动态',
  agentCategoryCode: 'COMPANY', classificationConfidence: 0.65, classificationReason: '公司发布新产品',
  reviewStatus: 'PENDING_REVIEW', manuallyReviewed: false
};

const snapshot = {
  overview: { eventCount: 1, highPriorityCount: 1, watchlistRelatedCount: 1, sourceCount: 3 },
  events: [event],
  latestChanges: [event],
  liveItems: [],
  warnings: [],
  refreshedAt: '2026-07-31T16:00:00'
};

const detail = {
  event,
  signals: [
    { id: 1, title: event.title, content: '新品正式发布。', url: 'https://example.com/1', sourceName: '财联社', sourceTier: 'TIER_1', publishedAt: liveItem.publishedAt, relationType: 'PRIMARY', matchScore: 1, matchReason: '代表信号' },
    { id: 2, title: '宁德时代新电池正式发布', content: '发布会信息。', sourceName: '同花顺', sourceTier: 'TIER_1', publishedAt: liveItem.publishedAt, relationType: 'SUPPORTING', matchScore: 0.84, matchReason: '主体、动作和标题语义一致' }
  ],
  evidence: [
    { id: 31, toolCode: 'research_material_search', evidenceType: 'ANNOUNCEMENT', title: '深交所公告', summary: '公司披露新产品量产安排。', url: 'https://example.com/announcement', sourceName: '深交所', sourceTier: 'T1', publishedAt: '2026-07-31T15:30:00' }
  ],
  agentTrace: [
    { nodeName: 'radar-evidence-plan', status: 'SUCCESS', summary: 'actions=2', durationMs: 920, fallbackUsed: false }
  ],
  workspaceState: { eventId: 10, read: true, followed: false, disposition: 'ACTIVE', readAt: '2026-07-31T16:01:00' },
  observations: [{ id: 51, eventId: 10, content: '观察公司正式公告', status: 'OPEN', source: 'SYSTEM', createdAt: '2026-07-31T16:01:00' }],
  timeline: [{ id: 61, eventId: 10, eventType: 'SIGNAL', title: '新增来源消息', summary: '同花顺补充量产信息', occurredAt: '2026-07-31T15:55:00' }],
  trust: { independentSourceCount: 3, sourceTierCounts: { TIER_1: 3 }, citationCoveredCount: 2, citationTotalCount: 2, concentration: '来源较分散', conflicts: [], limitation: '仅基于当前已收集证据' },
  researchLinks: [{ id: 71, eventId: 10, researchRunId: 41, questionSnapshot: '量产节奏是否兑现？', status: 'SUCCESS', summary: '量产仍需跟踪', createdAt: '2026-07-31T16:02:00' }],
  interpretation: {
    id: 41, eventId: 10, status: 'SUCCESS', stale: false, durationMs: 1280,
    result: {
      factSummary: '公司发布新产品，两家来源确认发布事实。',
      newDevelopment: '新增量产时间信息。',
      whyItMatters: '量产节奏可能影响相关产业链订单预期。',
      impactChain: ['产品发布→量产验证→供应链订单'],
      uncertainties: ['价格尚未披露'],
      nextObservations: ['观察公司正式公告'],
      evidenceRefs: ['signal:1', 'evidence:31']
    }
  }
};

const categories = [
  { code: 'COMPANY', name: '公司动态', enabled: true, displayOrder: 10 },
  { code: 'INDUSTRY', name: '行业产业', enabled: true, displayOrder: 20 }
];

const newsSnapshot = {
  refreshedAt: '2026-07-31T16:00:00',
  sourceCount: 2,
  warnings: [],
  categoryCounts: { ALL: 2, COMPANY: 2, INDUSTRY: 0, PENDING_REVIEW: 1 },
  unclassifiedCount: 1,
  items: [
    liveItem,
    { ...liveItem, id: 'THS:article', kind: 'ARTICLE', title: '上市公司要闻精华', providerCode: 'THS', sourceName: '同花顺' }
  ]
};

beforeEach(() => {
  vi.useRealTimers();
  vi.mocked(api).mockReset();
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    if (path === '/api/research-radar/events/10') return Promise.resolve(detail);
    if (path === '/api/research-radar/events/10/state') return Promise.resolve({ eventId: 10, read: true, followed: true, disposition: 'ACTIVE' });
    if (path === '/api/research-radar/notifications?limit=30') return Promise.resolve({ items: [], unreadCount: 0, todayCount: 0 });
    return Promise.resolve(snapshot);
  });
});

async function openRadar() {
  fireEvent.click(screen.getByRole('button', { name: '研究雷达' }));
  await act(async () => { await Promise.resolve(); });
  expect(screen.getByRole('heading', { name: '高优先级事件' })).toBeInTheDocument();
}

test('keeps the original realtime wire as the default and offers radar as a secondary view', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '实时快讯' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '要闻精华' })).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/news?category=ALL&limit=100');

  await openRadar();
  expect(api).toHaveBeenCalledWith('/api/research-radar?category=ALL&watchlistOnly=false&limit=20&state=ALL');
});

test('shows category quality counts and explainable agent decisions', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);

  expect(await screen.findByRole('button', { name: '公司动态 2' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '待确认 1' })).toBeInTheDocument();
  expect(screen.getByText('待分类 1')).toBeInTheDocument();
  expect(screen.getAllByText('65%').length).toBeGreaterThan(0);
  expect(screen.getAllByText('公司发布新产品').length).toBeGreaterThan(0);
  expect(screen.getAllByText('待确认').length).toBeGreaterThan(0);
});

test('uses the existing realtime view when switching to pending review', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await screen.findByRole('button', { name: '待确认 1' });

  fireEvent.click(screen.getByRole('button', { name: '待确认 1' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/news?category=PENDING_REVIEW&limit=100'));
  expect(screen.getByRole('heading', { name: '实时快讯' })).toBeInTheDocument();
});

test('corrects a classification and reloads the current realtime category', async () => {
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path === '/api/news/classifications/review') return Promise.resolve({
      itemId: 'CLS:1', agentCategoryCode: 'COMPANY', effectiveCategoryCode: 'INDUSTRY',
      agentConfidence: 0.65, agentReason: '公司发布新产品', reviewStatus: 'CORRECTED'
    });
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    return Promise.resolve(snapshot);
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  const reviewButtons = await screen.findAllByRole('button', { name: '确认或修正分类' });

  await userEvent.click(reviewButtons[0]);
  await userEvent.selectOptions(screen.getAllByLabelText('调整分类')[0], 'INDUSTRY');
  await userEvent.type(screen.getAllByLabelText('复核备注')[0], '产业链影响');
  await userEvent.click(screen.getAllByRole('button', { name: '保存分类' })[0]);

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/news/classifications/review', {
    method: 'POST',
    body: JSON.stringify({ itemId: 'CLS:1', categoryCode: 'INDUSTRY', reason: '产业链影响' })
  }));
  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/news?category=ALL&limit=100'));
});

test('keeps the news visible when classification review fails', async () => {
  const addToast = vi.fn();
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path === '/api/news/classifications/review') return Promise.reject(new Error('复核保存失败'));
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    return Promise.resolve(snapshot);
  });
  render(<NewsView setMessage={vi.fn()} addToast={addToast} onResearch={vi.fn()} />);
  await userEvent.click((await screen.findAllByRole('button', { name: '确认或修正分类' }))[0]);

  await userEvent.click(screen.getAllByRole('button', { name: '保存分类' })[0]);

  await waitFor(() => expect(addToast).toHaveBeenCalledWith('复核保存失败', 'error'));
  expect(screen.getAllByText('宁德时代发布新一代电池').length).toBeGreaterThan(0);
});

test('renders latest changes and full-width priority cards without duplicating the live wire', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();

  const latest = await screen.findByRole('heading', { name: '最新变化' });
  const focus = screen.getByRole('heading', { name: '高优先级事件' });
  expect(screen.getByText('与自选「宁德时代」直接相关')).toBeInTheDocument();
  expect(screen.getByText('92')).toBeInTheDocument();
  const board = screen.getByTestId('research-radar-board');
  expect(within(board).getAllByRole('heading')[0]).toBe(latest);
  expect(within(board).getAllByRole('heading')).toContain(focus);
  expect(screen.queryByRole('heading', { name: '实时发生' })).not.toBeInTheDocument();
});

test('loads original signals only when the user opens the interpretation drawer', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();
  await screen.findByRole('heading', { name: event.title });

  await userEvent.click(screen.getByRole('button', { name: '查看解读' }));

  expect(await screen.findByRole('dialog', { name: event.title })).toBeInTheDocument();
  expect(screen.getByText('3 个独立来源共同报道')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '证据' }));
  expect(screen.getByText('同花顺')).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/research-radar/events/10');
});

test('shows external evidence and a sanitized agent trace without prompts', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();
  await userEvent.click(await screen.findByRole('button', { name: '查看解读' }));

  expect(await screen.findByText('量产节奏可能影响相关产业链订单预期。')).toBeInTheDocument();
  await userEvent.click(await screen.findByRole('button', { name: '证据' }));
  expect(screen.getByRole('link', { name: '深交所公告' })).toBeInTheDocument();
  expect(screen.getByText('证据规划')).toBeInTheDocument();
  expect(screen.getByText(/actions=2/)).toBeInTheDocument();
  expect(screen.queryByText(/完整提示词/)).not.toBeInTheDocument();
});

test('keeps tracking details behind explicit dossier tabs', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar(); await userEvent.click(await screen.findByRole('button', { name: '查看解读' }));
  await userEvent.click(await screen.findByRole('button', { name: '事件脉络' }));
  expect(await screen.findByText('新增来源消息')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '证据' }));
  expect(screen.getByText('2/2')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '跟踪' }));
  expect(screen.getByText('观察公司正式公告')).toBeInTheDocument();
  expect(screen.getByText('研究运行 #41')).toBeInTheDocument();
});

test('does not load reminders until the user opens them', async () => {
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />); await openRadar();
  expect(api).not.toHaveBeenCalledWith('/api/research-radar/notifications?limit=30');
  await userEvent.click(screen.getByRole('button', { name: /关注提醒/ }));
  expect(api).toHaveBeenCalledWith('/api/research-radar/notifications?limit=30');
});

test('research action only hands the suggested question to the parent', async () => {
  const onResearch = vi.fn();
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={onResearch} />);
  await openRadar();
  await screen.findByRole('heading', { name: event.title });

  await userEvent.click(screen.getByRole('button', { name: '围绕此事研究' }));

  expect(onResearch).toHaveBeenCalledWith(event.id, event.suggestedResearchQuestion);
  expect(api).not.toHaveBeenCalledWith('/api/research/runs', expect.anything());
});

test('keeps research priority as the primary score and exposes hotspot score as context', async () => {
  const rankedEvent = { ...event, hotspotScore: 95, hotspotExplanation: '多源确认且来源排名靠前' };
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    return Promise.resolve({ ...snapshot, events: [rankedEvent], latestChanges: [rankedEvent] });
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();

  expect(screen.getByLabelText('研究优先级 92 分')).toBeInTheDocument();
  expect(screen.getByText('热点 95')).toBeInTheDocument();
});

test('supports watchlist-only filtering and degraded snapshots', async () => {
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    return Promise.resolve({ ...snapshot, warnings: ['实时资讯暂不可用，已展示最近一次结果'] });
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();
  await screen.findByRole('button', { name: '与我相关' });

  fireEvent.click(screen.getByRole('button', { name: '与我相关' }));

  await waitFor(() => expect(api).toHaveBeenCalledWith('/api/research-radar?category=ALL&watchlistOnly=true&limit=20&state=ALL'));
  expect(screen.getByText('实时来源暂不可用，当前展示最近一次雷达结果')).toBeInTheDocument();
});

test('describes a busy radar refresh without blaming realtime sources', async () => {
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    return Promise.resolve({ ...snapshot, warnings: ['雷达正在刷新，已展示最近一次结果'] });
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();

  expect(screen.getByText('雷达正在后台生产，当前展示最近一次热点快照')).toBeInTheDocument();
  expect(screen.queryByText('实时来源暂不可用，当前展示最近一次雷达结果')).not.toBeInTheDocument();
});

test('polling applies newly ranked radar events without waiting for confirmation', async () => {
  vi.useFakeTimers();
  let calls = 0;
  const updatedEvent = { ...event, id: 11, title: '新的雷达事件', lastSeenAt: '2026-07-31T16:01:00' };
  const updated = { ...snapshot, events: [updatedEvent, ...snapshot.events], latestChanges: [updatedEvent, event] };
  vi.mocked(api).mockImplementation((path) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    calls += 1; return Promise.resolve(calls === 1 ? snapshot : updated);
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();
  await act(async () => { await Promise.resolve(); });

  await act(async () => { vi.advanceTimersByTime(45_000); await Promise.resolve(); });

  expect(screen.getAllByText('新的雷达事件').length).toBeGreaterThan(0);
  expect(screen.queryByRole('button', { name: /发现 .* 条新资讯/ })).not.toBeInTheDocument();
});

test('opens immediately and generates a missing interpretation in the background', async () => {
  vi.useFakeTimers();
  let detailCalls = 0;
  vi.mocked(api).mockImplementation((path, options) => {
    if (path === '/api/news/categories') return Promise.resolve(categories);
    if (path.startsWith('/api/news?')) return Promise.resolve(newsSnapshot);
    if (path === '/api/research-radar/events/10/interpretation' && options?.method === 'POST') {
      return Promise.resolve({ eventId: 10, status: 'QUEUED', stale: false });
    }
    if (path === '/api/research-radar/events/10') {
      detailCalls += 1;
      return Promise.resolve(detailCalls === 1 ? { ...detail, interpretation: undefined } : detail);
    }
    return Promise.resolve(snapshot);
  });
  render(<NewsView setMessage={vi.fn()} addToast={vi.fn()} onResearch={vi.fn()} />);
  await openRadar();

  fireEvent.click(screen.getByRole('button', { name: '查看解读' }));
  await act(async () => { await Promise.resolve(); await Promise.resolve(); });

  expect(screen.getByRole('dialog', { name: event.title })).toBeInTheDocument();
  expect(api).toHaveBeenCalledWith('/api/research-radar/events/10/interpretation', { method: 'POST' });
  await act(async () => { vi.advanceTimersByTime(1_500); await Promise.resolve(); await Promise.resolve(); });
  expect(screen.getByText('量产节奏可能影响相关产业链订单预期。')).toBeInTheDocument();
});
