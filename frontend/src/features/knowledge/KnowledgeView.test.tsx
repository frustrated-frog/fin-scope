import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { beforeEach, expect, test, vi } from 'vitest';

import { apiEnvelope } from '../../test/apiEnvelope';
import { KnowledgeView } from './KnowledgeView';

const overview = {
  acceptedTaskCount: 2,
  suggestedTaskCount: 5,
  dueReviewCount: 1,
  activeTopicCount: 3,
  actions: [],
  activeTopics: [],
  recentEntries: []
};

const radar = {
  overview: { eventCount: 1, highPriorityCount: 1, watchlistRelatedCount: 0, sourceCount: 2 },
  events: [{ id: 7, title: '行业报价发生变化', summary: '两个来源指向报价变化', priorityScore: 80, recommendation: '重点关注', reasons: ['多来源'], watchlistRelated: false, watchlistExplanation: '', sourceCount: 2, signalCount: 2, uncertainty: '持续性未知', nextObservation: '观察订单', suggestedResearchQuestion: '' }],
  liveItems: [{ id: 'f1', kind: 'FLASH', title: '公司披露订单', content: '订单同比增加', providerCode: 'CLS', sourceName: '财联社', sourceTier: 'MEDIA' }],
  warnings: [],
  refreshedAt: '2026-08-01T10:00:00'
};

beforeEach(() => {
  window.history.replaceState({}, '', '/?section=topics&topic=11');
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const value = url.startsWith('/api/events/paged') || url.startsWith('/api/evidence/paged')
      ? { items: [], totalCount: 0, page: 0, pageSize: 100, totalPages: 0 }
      : url === '/api/knowledge/investment-recognitions'
      ? []
      : url === '/api/knowledge/topics/11'
      ? {
        topic: { id: 11, name: 'Agent 工程化', lifecycleStatus: 'ACTIVE', masteryStatus: 'BUILDING', revision: 1 },
        events: [], evidence: [], tasks: [], entries: []
      }
      : url.startsWith('/api/knowledge/topics')
      ? { items: [], totalCount: 0, page: 0, pageSize: 20, totalPages: 0 }
      : url.startsWith('/api/research-radar')
      ? radar
      : overview;
    return { ok: true, status: 200, text: async () => JSON.stringify(apiEnvelope(value)) } as Response;
  }));
});

test('restores a valid knowledge section and selected topic from the URL', async () => {
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: 'Agent 工程化' })).toBeInTheDocument();
  expect(screen.getByRole('navigation', { name: '知识工作台' })).toBeInTheDocument();
  expect(screen.getByTestId('knowledge-view')).toHaveAttribute('data-topic-id', '11');
  expect(fetch).toHaveBeenCalledWith(
    '/api/knowledge/topics/11',
    expect.anything()
  );
});

test('rejects unknown sections and keeps navigation state in the URL', async () => {
  window.history.replaceState({}, '', '/?section=unknown');
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '今天哪些变化，值得修正我的判断？' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '核验队列' }));

  await waitFor(() => {
    expect(new URLSearchParams(window.location.search).get('section')).toBe('facts');
  });
});

test('restores the workbench when browser history changes', async () => {
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(await screen.findByRole('heading', { name: 'Agent 工程化' })).toBeInTheDocument();

  window.history.replaceState({}, '', '/?section=home');
  window.dispatchEvent(new PopStateEvent('popstate'));

  expect(await screen.findByRole('heading', { name: '今天哪些变化，值得修正我的判断？' })).toBeInTheDocument();
  expect(screen.getByTestId('knowledge-view')).not.toHaveAttribute('data-topic-id');
});

test('lets the verification queue own the page hierarchy without repeating the generic knowledge hero', async () => {
  window.history.replaceState({}, '', '/?section=facts');
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('当前没有需要核验的投资命题')).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '把信息变成可复用的判断' })).not.toBeInTheDocument();
  expect(screen.getByRole('navigation', { name: '知识工作台' })).toBeInTheDocument();
});

test('loads verification propositions only from formed recognition workspaces', async () => {
  window.history.replaceState({}, '', '/?section=facts');
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const value = url === '/api/knowledge/investment-recognitions'
      ? [{ id: 21, status: 'ACCEPTED', topicId: 7, revision: 1 }]
      : url.startsWith('/api/knowledge/topics?page=0&size=100')
      ? {
        items: [
          { id: 7, name: '先进封装供需上行', lifecycleStatus: 'ACTIVE', masteryStatus: 'REVIEWING', revision: 2, articleCount: 0 },
          { id: 8, name: '一篇文章的自动主题', lifecycleStatus: 'ACTIVE', masteryStatus: 'EXPLORING', revision: 0, articleCount: 1 }
        ],
        totalCount: 2,
        page: 0,
        pageSize: 100,
        totalPages: 1
      }
      : url === '/api/knowledge/topics/7'
        ? {
          topic: { id: 7, name: '先进封装供需上行', lifecycleStatus: 'ACTIVE', masteryStatus: 'REVIEWING', revision: 2, articleCount: 0 },
          events: [{ id: 31, canonicalTitle: '公司季度经营更新' }],
          evidence: [{
            id: 1,
            eventId: 31,
            sourceTier: 'MEDIA',
            evidenceType: 'FACT',
            claim: '公司披露二季度先进封装收入同比增长 28%。',
            confidence: 76,
            articleTitle: '季度经营数据报道',
            articleUrl: 'https://news.example.com/quarter'
          }],
          tasks: [],
          entries: []
        }
        : {};
    return { ok: true, status: 200, text: async () => JSON.stringify(apiEnvelope(value)) } as Response;
  });
  vi.stubGlobal('fetch', fetchMock);

  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '公司披露二季度先进封装收入同比增长 28%。' })).toBeInTheDocument();
  expect(fetchMock).toHaveBeenCalledWith('/api/knowledge/topics/7', expect.anything());
  expect(fetchMock).not.toHaveBeenCalledWith('/api/knowledge/topics/8', expect.anything());
  expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/events/paged'))).toBe(false);
  expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/evidence/paged'))).toBe(false);
});

test('exposes only the three primary knowledge tasks and clears stale selection', async () => {
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);
  expect(await screen.findByRole('heading', { name: 'Agent 工程化' })).toBeInTheDocument();

  const navigation = screen.getByRole('navigation', { name: '知识工作台' });
  expect(navigation).toHaveTextContent('今日研究');
  expect(navigation).toHaveTextContent('核验队列');
  expect(navigation).toHaveTextContent('投资认识');
  expect(navigation).not.toHaveTextContent('学习队列');
  expect(navigation).not.toHaveTextContent('到期复习');

  await userEvent.click(screen.getByRole('button', { name: '核验队列' }));

  await waitFor(() => {
    const params = new URLSearchParams(window.location.search);
    expect(params.get('topic')).toBeNull();
    expect(params.get('task')).toBeNull();
    expect(screen.getByTestId('knowledge-view')).not.toHaveAttribute('data-topic-id');
  });
});

test('loads daily changes and flashes into the knowledge workbench', async () => {
  window.history.replaceState({}, '', '/?section=home');
  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByText('行业报价发生变化')).toBeInTheDocument();
  expect(screen.getByRole('complementary', { name: '今日快讯流水' })).toHaveTextContent('公司披露订单');
  expect(fetch).toHaveBeenCalledWith('/api/research-radar?category=ALL&watchlistOnly=false&limit=8', expect.anything());
  expect(screen.queryByRole('heading', { name: '把信息变成可复用的判断' })).not.toBeInTheDocument();
});

test('keeps knowledge actions visible when daily news fails', async () => {
  window.history.replaceState({}, '', '/?section=home');
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.startsWith('/api/research-radar')) throw new Error('offline');
    return { ok: true, status: 200, text: async () => JSON.stringify(apiEnvelope(overview)) } as Response;
  }));

  render(<KnowledgeView addToast={vi.fn()} setMessage={vi.fn()} />);

  expect(await screen.findByRole('heading', { name: '需要更新的认识' })).toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveTextContent('今日资讯暂不可用');
});

test('centers and scales the four knowledge navigation labels', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.knowledge-nav-item\s*{[^}]*align-items:\s*center;/s);
  expect(styles).toMatch(/\.knowledge-nav-item\s*{[^}]*justify-content:\s*center;/s);
  expect(styles).toMatch(/\.knowledge-nav-item\s*{[^}]*gap:\s*8px;/s);
  expect(styles).toMatch(/\.knowledge-nav-item\s*{[^}]*white-space:\s*nowrap;/s);
  expect(styles).toMatch(
    /\.knowledge-nav-item span\s*{[^}]*font-size:\s*15px;[^}]*font-weight:\s*650;[^}]*line-height:\s*1\.2;/s
  );
  expect(styles).toMatch(
    /\.knowledge-nav-item small\s*{[^}]*font-size:\s*11px;[^}]*font-weight:\s*500;[^}]*line-height:\s*1\.2;/s
  );
});
