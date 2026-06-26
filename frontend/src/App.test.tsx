import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import App from './App';

const responses: Record<string, unknown> = {
  '/api/dashboard': { sourceCount: 2, articleCount: 3, briefCount: 1, latestFetchRuns: [] },
  '/api/sources': [
    { id: 1, name: '测试财经RSS', type: 'RSS', url: 'https://example.com/rss', enabled: true, credibility: 4, tags: '宏观' }
  ],
  '/api/articles': [
    {
      id: 1,
      title: '美联储释放降息信号 黄金走强',
      sourceName: '测试财经RSS',
      noveltyType: 'NEW',
      noveltyReason: '首次进入信息流',
      category: '宏观',
      insightCard: {
        oneSentenceSummary: '美联储释放偏鸽信号，黄金价格继续走强。',
        coreEvent: '市场重新交易降息预期。',
        importance: '影响利率预期、资产定价和风险偏好。',
        impactTargets: '黄金、美元、债券、权益市场',
        followUpQuestions: '下一次验证窗口是什么？',
        cardMarkdown: '## 情报卡片'
      }
    }
  ],
  '/api/articles/paged?page=0&pageSize=20': {
    items: [
      {
        id: 1,
        title: '美联储释放降息信号 黄金走强',
        sourceName: '测试财经RSS',
        noveltyType: 'NEW',
        noveltyReason: '首次进入信息流',
        category: '宏观',
        insightCard: {
          oneSentenceSummary: '美联储释放偏鸽信号，黄金价格继续走强。',
          coreEvent: '市场重新交易降息预期。',
          importance: '影响利率预期、资产定价和风险偏好。',
          impactTargets: '黄金、美元、债券、权益市场',
          followUpQuestions: '下一次验证窗口是什么？',
          cardMarkdown: '## 情报卡片'
        }
      }
    ],
    totalCount: 1,
    page: 0,
    pageSize: 20,
    totalPages: 1
  },
  '/api/articles/ingest-url': {
    article: { id: 2, title: '粘贴 URL 生成情报卡片', sourceName: '手动研究', noveltyType: 'NEW', category: '市场' },
    insightCard: {
      oneSentenceSummary: '网页内容已经被整理成固定格式卡片。',
      coreEvent: '用户手动导入 URL。',
      importance: '将临时阅读变成可复用信息资产。',
      impactTargets: '个人学习、每日简报、自媒体选题',
      followUpQuestions: '下一次应该追踪什么？'
    }
  },
  '/api/briefs': [
    { id: 1, briefDate: '2026-06-23', title: 'FinScope Daily Brief - 2026-06-23', markdownPath: 'data/vault/daily-briefs/2026-06-23.md' }
  ],
  '/api/topics': [
    {
      id: 1,
      name: '降息交易',
      status: 'LEARNING',
      description: '围绕利率预期的资产定价主题',
      articleCount: 2,
      briefCount: 1,
      terms: '美联储,降息,黄金',
      learningQuestions: '为什么降息会影响黄金？\n如何判断预期差？',
      markdownPath: 'data/vault/topics/jiang-xi-jiao-yi.md'
    }
  ],
  '/api/topics/1': {
    topic: {
      id: 1,
      name: '降息交易',
      status: 'LEARNING',
      description: '围绕利率预期的资产定价主题',
      articleCount: 2,
      briefCount: 1,
      terms: '美联储,降息,黄金',
      learningQuestions: '为什么降息会影响黄金？\n如何判断预期差？',
      markdownPath: 'data/vault/topics/jiang-xi-jiao-yi.md'
    },
    linkedArticles: [
      { id: 1, title: '美联储释放降息信号 黄金走强', sourceName: '测试财经RSS', noveltyType: 'NEW' }
    ],
    linkedBriefs: [
      { id: 1, briefDate: '2026-06-23', title: 'FinScope Daily Brief - 2026-06-23', markdownPath: 'data/vault/daily-briefs/2026-06-23.md' }
    ],
    markdown: '# 降息交易\n\n## 个人理解\n\n- 暂无'
  },
  '/api/agent-runs': [
    { id: 1, nodeName: 'brief-generate', status: 'SUCCESS', durationMs: 12 }
  ]
};

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/articles/ingest-url' && String(init?.body).includes('x-shell')) {
      return {
        ok: false,
        status: 400,
        json: async () => ({ error: '未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页' })
      } as Response;
    }
    return {
      ok: true,
      json: async () => responses[url] ?? {}
    } as Response;
  }));
});

test('renders the FinScope workspace shell and dashboard metrics', async () => {
  render(<App />);

  expect(screen.getByText('FinScope')).toBeInTheDocument();
  expect(await screen.findByText('信息源')).toBeInTheDocument();
  expect(screen.getByText('文章池')).toBeInTheDocument();
  expect(screen.getByText('简报')).toBeInTheDocument();
});

test('topbar status controls use matching pills and icon theme toggle', async () => {
  render(<App />);

  expect(await screen.findByText('Articles')).toBeInTheDocument();

  const topbarActions = document.querySelector('.topbar-actions') as HTMLElement;
  const topbar = within(topbarActions);
  const articlesChip = topbar.getByText('Articles').closest('.topbar-pill');
  const topicsChip = topbar.getByText('Topics').closest('.topbar-pill');
  const themeButton = topbar.getByRole('button', { name: '切换为浅色模式' });
  const refreshButton = topbar.getByRole('button', { name: '刷新' });
  const statusChip = topbar.getByText('准备就绪').closest('.topbar-pill');

  expect(articlesChip).toBeTruthy();
  expect(topicsChip).toBeTruthy();
  expect(themeButton).toHaveClass('topbar-pill');
  expect(refreshButton).toHaveClass('topbar-pill');
  expect(statusChip).toBeTruthy();
  expect(themeButton).toHaveTextContent('☀');
  expect(themeButton).not.toHaveTextContent('浅色');
});

test('switches to inbox and shows novelty reasoning', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Inbox' }));

  expect(await screen.findByText('美联储释放降息信号 黄金走强')).toBeInTheDocument();
  expect(screen.getByText('首次进入信息流')).toBeInTheDocument();
  expect(screen.getByText('一句话摘要')).toBeInTheDocument();
  expect(screen.getByText('市场重新交易降息预期。')).toBeInTheDocument();
});

test('can ingest a pasted url from inbox as an insight card', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Inbox' }));
  await userEvent.type(await screen.findByLabelText('文章 URL'), 'https://example.com/article');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(fetch).toHaveBeenCalledWith('/api/articles/ingest-url', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ url: 'https://example.com/article', sourceName: '手动研究', tags: '市场' })
  }));
});

test('shows readable error when pasted url cannot be extracted', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Inbox' }));
  await userEvent.type(await screen.findByLabelText('文章 URL'), 'https://x.com/x-shell');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(await screen.findByText('未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页')).toBeInTheDocument();
});

test('can compound an inbox article into a topic', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Inbox' }));
  await userEvent.click(await screen.findByRole('button', { name: '沉淀主题' }));

  expect(fetch).toHaveBeenCalledWith('/api/topics/from-article/1', expect.objectContaining({ method: 'POST' }));
});

test('batch selection controls use the same compact pill shape', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Inbox' }));
  await userEvent.click(await screen.findByLabelText('全选当前页'));

  const selectedBadge = await screen.findByText('已选 1 项');
  const deleteButton = screen.getByRole('button', { name: '删除所选' });

  expect(selectedBadge).toHaveClass('selection-pill');
  expect(deleteButton).toHaveClass('selection-pill');
});

test('topics show learning metadata and vault path', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Topics' }));

  expect(await screen.findByText('降息交易')).toBeInTheDocument();
  expect(screen.getByText('关联文章 2')).toBeInTheDocument();
  expect(screen.getByText('关联简报 1')).toBeInTheDocument();
  expect(screen.getByText('美联储,降息,黄金')).toBeInTheDocument();
  expect(screen.getByText('data/vault/topics/jiang-xi-jiao-yi.md')).toBeInTheDocument();
});

test('learning view opens a topic and appends personal understanding', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Learning' }));
  await userEvent.click(await screen.findByRole('button', { name: '记录理解' }));
  await screen.findByText('为什么降息会影响黄金？');
  await userEvent.selectOptions(screen.getByLabelText('学习状态'), 'REVIEWING');
  await userEvent.type(screen.getByLabelText('个人理解'), '我理解的核心变量是利率预期。');
  await userEvent.click(screen.getByRole('button', { name: '保存理解' }));

  expect(fetch).toHaveBeenCalledWith('/api/topics/1/notes', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ status: 'REVIEWING', note: '我理解的核心变量是利率预期。' })
  }));
});

test('learning queue action buttons keep a fixed single-line size', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Learning' }));

  const noteButtons = await screen.findAllByRole('button', { name: '记录理解' });

  expect(noteButtons.length).toBeGreaterThan(0);
  noteButtons.forEach((button) => {
    expect(button).toHaveClass('learning-action-button');
  });
});
