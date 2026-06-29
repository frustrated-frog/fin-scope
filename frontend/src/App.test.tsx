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
      url: 'https://x.com/tester/status/123',
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
        url: 'https://x.com/tester/status/123',
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
    { id: 1, briefDate: '2026-06-25', title: '每日金融、投资、创业学习简报 - 2026-06-25', markdownPath: 'data/vault/daily-briefs/2026-06-25.md' }
  ],
  '/api/briefs/2026-06-25': {
    id: 1,
    briefDate: '2026-06-25',
    title: '每日金融、投资、创业学习简报 - 2026-06-25',
    markdownPath: 'data/vault/daily-briefs/2026-06-25.md',
    content: '# 每日金融、投资、创业学习简报 - 2026-06-25\n\n生成时间：2026-06-25 09:16 CST  \n定位：帮助建立长期判断力，不提供具体买卖建议。\n\n## 今日摘要\n\n1. 市场正在从“流动性和主题催化”转向“制度、融资窗口和资本效率”的再定价。\n\n## 中国观察\n\n### 金融法与央行法修订\n\n**发生了什么**\n\n政策重点从刺激增长转向金融系统治理能力。\n\n## 今日思考题\n\n1. 如果你是投资人，会优先看什么？'
  },
  '/api/briefs/2026-06-25/research-context': {
    briefDate: '2026-06-25',
    events: [
      {
        id: 1,
        canonicalTitle: '美联储降息预期升温，黄金ETF出现增量资金',
        themeCode: 'china_macro',
        summary: '市场重新交易实际利率下行与黄金定价。',
        noveltyState: 'FOLLOW_UP',
        evidenceCount: 2,
        articleCount: 2,
        importanceScore: 86
      },
      {
        id: 2,
        canonicalTitle: '央行开展3000亿元MLF操作并下调利率10个基点',
        themeCode: 'china_macro',
        summary: '政策工具释放更明确的宽松信号。',
        noveltyState: 'NEW',
        evidenceCount: 1,
        articleCount: 1,
        importanceScore: 82
      }
    ],
    evidenceItems: [
      {
        id: 1,
        eventId: 1,
        sourceTier: 'MEDIA',
        evidenceType: 'DATA',
        claim: '黄金ETF单周流入12亿美元。',
        confidence: 75
      },
      {
        id: 2,
        eventId: 2,
        sourceTier: 'REGULATOR',
        evidenceType: 'TIMELINE',
        claim: '央行开展3000亿元MLF操作并下调利率10个基点。',
        confidence: 90
      }
    ],
    learningTasks: [
      {
        id: 1,
        eventId: 1,
        themeCode: 'china_macro',
        question: '为什么实际利率下行会推升黄金配置需求？',
        status: 'TODO'
      },
      {
        id: 2,
        eventId: 2,
        themeCode: 'china_macro',
        question: 'MLF、逆回购和LPR分别如何传导到融资成本？',
        status: 'TODO'
      }
    ],
    contentIdeas: [
      {
        id: 1,
        eventId: 1,
        title: '为什么市场还没等到降息，黄金已经先涨了？',
        angle: '用实际利率和预期差解释黄金先涨的逻辑。',
        format: 'X_THREAD',
        score: 84,
        scoreReason: '证据强度够高，而且能沉淀成长期有效的宏观解释框架。',
        outline: '1. 先看降息预期\n2. 再看实际利率\n3. 最后看资金流向'
      }
    ]
  },
  '/api/briefs/2026-06-23': {
    id: 2,
    briefDate: '2026-06-23',
    title: 'FinScope Daily Brief - 2026-06-23',
    markdownPath: 'data/vault/daily-briefs/2026-06-23.md',
    content: '# FinScope Daily Brief - 2026-06-23\n\n## 今日摘要\n\n测试简报。'
  },
  '/api/topics/from-brief/2026-06-25': [
    { id: 1, name: '每日简报主题', status: 'LEARNING' }
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
  '/api/events': [
    {
      id: 1,
      canonicalTitle: '美联储降息预期升温，黄金ETF出现增量资金',
      themeCode: 'china_macro',
      status: 'ACTIVE',
      noveltyState: 'FOLLOW_UP',
      evidenceCount: 2,
      articleCount: 2,
      importanceScore: 86,
      firstSeenAt: '2026-06-25T09:00:00',
      lastSeenAt: '2026-06-25T18:00:00',
      summary: '市场重新交易实际利率下行与黄金定价。'
    },
    {
      id: 2,
      canonicalTitle: 'Claude Code 推出新的多代理工作流',
      themeCode: 'ai_startup',
      status: 'ACTIVE',
      noveltyState: 'NEW',
      evidenceCount: 1,
      articleCount: 1,
      importanceScore: 78,
      firstSeenAt: '2026-06-26T09:00:00',
      lastSeenAt: '2026-06-26T09:00:00',
      summary: 'AI 开发者生态继续往 agent workflow 演进。'
    }
  ],
  '/api/events/1/articles': [
    { eventId: 1, articleId: 1, noveltyType: 'NEW', articleTitle: '美联储释放降息信号 黄金走强', articleUrl: 'https://example.com/1' },
    { eventId: 1, articleId: 2, noveltyType: 'FOLLOW_UP', articleTitle: '黄金ETF单周流入12亿美元', articleUrl: 'https://example.com/2' }
  ],
  '/api/events/1/evidence': [
    { id: 1, eventId: 1, sourceTier: 'MEDIA', evidenceType: 'DATA', claim: '黄金ETF单周流入12亿美元。', confidence: 75 },
    { id: 2, eventId: 1, sourceTier: 'REGULATOR', evidenceType: 'TIMELINE', claim: '美联储官员释放偏鸽措辞。', confidence: 90 }
  ],
  '/api/events/2/articles': [
    { eventId: 2, articleId: 3, noveltyType: 'NEW', articleTitle: 'Claude Code 推出新的多代理工作流', articleUrl: 'https://example.com/3' }
  ],
  '/api/events/2/evidence': [
    { id: 3, eventId: 2, sourceTier: 'COMPANY', evidenceType: 'FACT', claim: '官方文档公布了多代理工作流能力。', confidence: 85 }
  ],
  '/api/evidence': [
    { id: 1, eventId: 1, sourceTier: 'MEDIA', evidenceType: 'DATA', claim: '黄金ETF单周流入12亿美元。', confidence: 75 },
    { id: 2, eventId: 1, sourceTier: 'REGULATOR', evidenceType: 'TIMELINE', claim: '美联储官员释放偏鸽措辞。', confidence: 90 },
    { id: 3, eventId: 2, sourceTier: 'COMPANY', evidenceType: 'FACT', claim: '官方文档公布了多代理工作流能力。', confidence: 85 }
  ],
  '/api/learning-tasks': [
    {
      id: 1,
      eventId: 1,
      themeCode: 'china_macro',
      question: '为什么实际利率下行会推升黄金配置需求？',
      concepts: '实际利率,黄金,预期差',
      difficulty: 'INTERMEDIATE',
      status: 'TODO'
    },
    {
      id: 2,
      eventId: 2,
      themeCode: 'ai_startup',
      question: '这个 agent workflow 更依赖模型能力还是开发者生态？',
      concepts: 'agent,工作流,开发者生态',
      difficulty: 'INTERMEDIATE',
      status: 'TODO'
    }
  ],
  '/api/content-ideas': [
    {
      id: 1,
      eventId: 1,
      themeCode: 'china_macro',
      title: '为什么市场还没等到降息，黄金已经先涨了？',
      angle: '用实际利率和预期差解释黄金先涨的逻辑。',
      format: 'X_THREAD',
      audience: '宏观投资学习者',
      score: 84,
      scoreReason: '证据强度够高，而且能沉淀成长期有效的宏观解释框架。',
      outline: '1. 先看降息预期\n2. 再看实际利率\n3. 最后看资金流向',
      status: 'IDEA'
    }
  ],
  '/api/agent-runs': [
    { id: 1, nodeName: 'brief-generate', status: 'SUCCESS', durationMs: 12 }
  ]
};

beforeEach(() => {
  const state = JSON.parse(JSON.stringify(responses)) as Record<string, any>;
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/articles/ingest-url' && String(init?.body).includes('x-shell')) {
      return {
        ok: false,
        status: 400,
        json: async () => ({ error: '未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页' })
      } as Response;
    }
    if (url === '/api/learning-tasks/1/status' && init?.method === 'POST') {
      const payload = JSON.parse(String(init.body));
      state['/api/learning-tasks'][0].status = payload.status;
      return {
        ok: true,
        json: async () => state['/api/learning-tasks'][0]
      } as Response;
    }
    if (url === '/api/content-ideas/1/status' && init?.method === 'POST') {
      const payload = JSON.parse(String(init.body));
      state['/api/content-ideas'][0].status = payload.status;
      return {
        ok: true,
        json: async () => state['/api/content-ideas'][0]
      } as Response;
    }
    return {
      ok: true,
      json: async () => state[url] ?? {}
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

test('theme toggle keeps the document root in sync with the active theme', async () => {
  render(<App />);

  expect(await screen.findByText('Articles')).toBeInTheDocument();
  expect(document.documentElement.dataset.theme).toBe('dark');

  await userEvent.click(screen.getByRole('button', { name: '切换为浅色模式' }));

  expect(document.documentElement.dataset.theme).toBe('light');
});

test('switches to inbox and shows novelty reasoning', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));

  expect(await screen.findByText('美联储释放降息信号 黄金走强')).toBeInTheDocument();
  expect(screen.getByText('NEW')).toBeInTheDocument();
  await userEvent.click(screen.getByText('美联储释放降息信号 黄金走强'));
  expect(screen.getByText('一句话摘要')).toBeInTheDocument();
  expect(screen.getByText('市场重新交易降息预期。')).toBeInTheDocument();
});

test('can ingest a pasted url from inbox as an insight card', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.type(await screen.findByPlaceholderText('输入文章URL...'), 'https://example.com/article');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(fetch).toHaveBeenCalledWith('/api/articles/ingest-url', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ url: 'https://example.com/article', sourceName: '手动研究', tags: '市场' })
  }));
});

test('shows readable error when pasted url cannot be extracted', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.type(await screen.findByPlaceholderText('输入文章URL...'), 'https://x.com/x-shell');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(await screen.findByText('未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页')).toBeInTheDocument();
});

test('can compound an inbox article into a topic', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.click(await screen.findByText('美联储释放降息信号 黄金走强'));
  await userEvent.click(await screen.findByText('沉淀到主题库'));

  expect(fetch).toHaveBeenCalledWith('/api/topics/from-article/1', expect.objectContaining({ method: 'POST' }));
});

test('batch selection controls use the same compact pill shape', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.click(await screen.findByLabelText('全选当前页'));

  const selectedBadge = await screen.findByText('已选 1 项');
  const deleteButton = screen.getByRole('button', { name: '删除所选' });

  expect(selectedBadge).toHaveClass('selection-pill');
  expect(deleteButton).toHaveClass('selection-pill');
});

test('inbox cards show detected source labels before the category tag', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));

  const sourceTag = await screen.findByText('X（推特）');
  const categoryTag = screen.getByText('宏观');

  expect(sourceTag).toHaveClass('article-source-tag');
  expect(sourceTag).toHaveClass('article-source-tag-social');
  expect(categoryTag).toHaveClass('article-category-tag');
});

test('article card delete action uses rounded rectangle danger styling', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.click(await screen.findByText('美联储释放降息信号 黄金走强'));

  const deleteButton = await screen.findByRole('button', { name: '删除' });

  expect(deleteButton).toHaveClass('article-danger-button');
});

test('opens a magazine-style brief reader from the briefs list', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Briefs' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看简报' }));

  expect(fetch).toHaveBeenCalledWith('/api/briefs/2026-06-25', expect.anything());
  expect(await screen.findByText('每日金融、投资、创业学习简报')).toBeInTheDocument();
  expect(screen.getAllByText('今日摘要').length).toBeGreaterThan(0);
  expect(screen.getAllByText('中国观察').length).toBeGreaterThan(0);
  expect(screen.getAllByText('今日思考题').length).toBeGreaterThan(0);
  expect(screen.getByRole('button', { name: '返回简报列表' })).toBeInTheDocument();
});

test('brief reader places the outline overview above the document body', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Briefs' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看简报' }));

  const overview = await screen.findByRole('complementary', { name: '简报大纲概览' });
  const document = screen.getByRole('region', { name: '简报正文' });

  expect(overview).toHaveClass('brief-reader-overview');
  expect(document).toHaveClass('brief-reader-document');
  expect(overview.compareDocumentPosition(document) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
});

test('events view shows research event cards with evidence and article counts', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Events' }));

  expect((await screen.findAllByText('美联储降息预期升温，黄金ETF出现增量资金')).length).toBeGreaterThan(0);
  expect(screen.getByText('FOLLOW_UP')).toBeInTheDocument();
  expect(screen.getByText(/证据 2/)).toBeInTheDocument();
  expect(screen.getByText(/文章 2/)).toBeInTheDocument();
});

test('events view can filter evidence by source tier and shows novelty distribution', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Events' }));

  expect(await screen.findByText('FOLLOW_UP 1')).toBeInTheDocument();
  expect(screen.getByText('NEW 1')).toBeInTheDocument();
  expect(screen.getByText(/\[MEDIA\].*黄金ETF单周流入12亿美元。/)).toBeInTheDocument();
  expect(screen.getByText(/\[REGULATOR\].*美联储官员释放偏鸽措辞。/)).toBeInTheDocument();

  await userEvent.selectOptions(screen.getByLabelText('证据来源层级'), 'REGULATOR');

  expect(screen.getByText(/\[REGULATOR\].*美联储官员释放偏鸽措辞。/)).toBeInTheDocument();
  expect(screen.queryByText(/\[MEDIA\].*黄金ETF单周流入12亿美元。/)).not.toBeInTheDocument();
});

test('evidence ledger shows source tiers and evidence types in one workspace', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Evidence' }));

  expect(await screen.findByText('证据账本')).toBeInTheDocument();
  expect(screen.getByText('黄金ETF单周流入12亿美元。')).toBeInTheDocument();
  expect(screen.getByText('REGULATOR')).toBeInTheDocument();
  expect(screen.getByText('TIMELINE')).toBeInTheDocument();
});

test('content studio shows idea score and outline for generated topics', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Studio' }));

  expect(await screen.findByText('为什么市场还没等到降息，黄金已经先涨了？')).toBeInTheDocument();
  expect(screen.getByText('84')).toBeInTheDocument();
  expect(screen.getByText('X_THREAD')).toBeInTheDocument();
  expect(screen.getByText('证据强度够高，而且能沉淀成长期有效的宏观解释框架。')).toBeInTheDocument();
  expect(screen.getByText(/1\. 先看降息预期/)).toBeInTheDocument();
});

test('brief reader shows research evidence, learning tasks and content ideas', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Briefs' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看简报' }));

  expect(await screen.findByText('研究上下文')).toBeInTheDocument();
  expect(screen.getByText('中国宏观 / FOLLOW_UP')).toBeInTheDocument();
  expect(screen.getByText('央行开展3000亿元MLF操作并下调利率10个基点')).toBeInTheDocument();
  expect(screen.getByText(/\[MEDIA\].*黄金ETF单周流入12亿美元。/)).toBeInTheDocument();
  expect(screen.getByText('为什么实际利率下行会推升黄金配置需求？')).toBeInTheDocument();
  expect(screen.getByText((content) => content.includes('为什么市场还没等到降息，黄金已经先涨了？'))).toBeInTheDocument();
  expect(screen.getByText((content) => content.includes('证据强度够高，而且能沉淀成长期有效的宏观解释框架。'))).toBeInTheDocument();
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

test('learning view can filter tasks by theme and jump to the related event', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Learning' }));
  await screen.findByText('为什么实际利率下行会推升黄金配置需求？');
  await screen.findByText('这个 agent workflow 更依赖模型能力还是开发者生态？');

  await userEvent.selectOptions(screen.getByLabelText('学习主题筛选'), 'china_macro');

  expect(screen.getByText('为什么实际利率下行会推升黄金配置需求？')).toBeInTheDocument();
  expect(screen.queryByText('这个 agent workflow 更依赖模型能力还是开发者生态？')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '查看关联事件-1' }));

  expect(await screen.findByText('事件记忆')).toBeInTheDocument();
  expect(screen.getAllByText('美联储降息预期升温，黄金ETF出现增量资金').length).toBeGreaterThan(0);
});

test('learning view updates research task status through the typed endpoint', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Learning' }));
  await screen.findByText('为什么实际利率下行会推升黄金配置需求？');

  await userEvent.selectOptions(screen.getByLabelText('学习任务状态-1'), 'LEARNING');
  await userEvent.click(screen.getByRole('button', { name: '更新任务状态-1' }));

  expect(fetch).toHaveBeenCalledWith('/api/learning-tasks/1/status', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ status: 'LEARNING' })
  }));
  expect(await screen.findByText('学习任务状态已更新')).toBeInTheDocument();
});

test('content studio updates idea status through the typed endpoint', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Studio' }));
  await screen.findByText('为什么市场还没等到降息，黄金已经先涨了？');

  await userEvent.selectOptions(screen.getByLabelText('内容选题状态-1'), 'DRAFTING');
  await userEvent.click(screen.getByRole('button', { name: '保存选题状态' }));

  expect(fetch).toHaveBeenCalledWith('/api/content-ideas/1/status', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ status: 'DRAFTING' })
  }));
  expect(await screen.findByText('选题状态已更新')).toBeInTheDocument();
});
