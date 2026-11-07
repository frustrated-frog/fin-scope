import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import App from './App';
import { AgentRunsView } from './features/agents/AgentRunsView';
import { mockApiResponse } from './test/apiEnvelope';

const dashboardRadarEvent = {
  id: 10,
  title: '央行宣布下调存款准备金率',
  summary: '本次调整预计释放长期流动性约一万亿元。',
  hotspotScore: 91,
  hotspotLifecycleState: 'RISING',
  priorityScore: 88,
  recommendation: '值得关注',
  reasons: ['多源确认', '政策影响面广'],
  watchlistRelated: false,
  watchlistExplanation: '',
  sourceCount: 3,
  signalCount: 5,
  uncertainty: '需要观察后续流动性传导效果。',
  nextObservation: '观察资金利率与信贷投放。',
  suggestedResearchQuestion: '降准如何影响流动性与风险资产？',
  lastSeenAt: '2026-08-06T09:30:00'
};

const responses: Record<string, unknown> = {
  '/api/investment-reactions/recent': [],
  '/api/investment-reactions/events?view=TRACKING&query=&page=1&size=20': { items: [], total: 0, counts: {}, stockNames: {}, stockCounts: {}, pendingReasons: {}, anchor: 0, revision: 0, page: 1, size: 20 },
  '/api/investment-reactions/followed': [],
  '/api/investment-reactions/discovery': { running: false, captured: 0, resolved: 0, message: '自动发现已开启' },
  '/api/investment-observations': {
    focus: [{
      id: 7, sourceType: 'RADAR_EVENT', sourceId: 10, title: '降准后的流动性传导进入验证期',
      summary: '政策已落地，下一步观察资金利率与信贷投放。', subjectType: 'EVENT', subjectName: '降准',
      stage: 'FOCUS', changeType: 'POLICY', score: 76, scoreDimensions: [],
      whyItMatters: '可能影响流动性、信贷与风险偏好。', uncertainty: '实体融资需求仍待验证。',
      nextValidation: '观察资金利率与新增信贷。', supportingEvidenceCount: 3, opposingEvidenceCount: 0,
      independentSourceCount: 3, disposition: 'ACTIVE', revision: 1, evidenceInsufficient: false,
      sourceAvailable: true, lastChangedAt: '2026-08-20T09:30:00'
    }],
    tracking: [], learning: [], archived: [], transitions: [], activeCount: 1, changedTodayCount: 1,
    waitingValidationCount: 1, archivedCount: 0, refreshedAt: '2026-08-20T10:00:00'
  },
  '/api/strategy/overview': { holdings: [], targetWeight: 0, currentWeight: 0 },
  '/api/strategy/playbooks': [],
  '/api/strategy/stock-theses': [],
  '/api/strategy/reviews': [],
  '/api/dashboard': {
    sourceCount: 2,
    articleCount: 3,
    briefCount: 1,
    latestFetchRuns: []
  },
  '/api/dashboard/hotspots': [
    {
      categoryCode: 'FINANCE',
      label: '金融',
      items: [{ ...dashboardRadarEvent, lifecycleState: dashboardRadarEvent.hotspotLifecycleState }]
    },
    { categoryCode: 'TECHNOLOGY', label: '科技', items: [] },
    { categoryCode: 'POLITICS', label: '政治', items: [] }
  ],
  '/api/research-radar?category=ALL&watchlistOnly=false&limit=20&state=ALL': {
    overview: { eventCount: 1, highPriorityCount: 1, watchlistRelatedCount: 0, sourceCount: 3 },
    events: [dashboardRadarEvent],
    warnings: [],
    refreshedAt: '2026-08-06T09:30:00',
    productionStatus: { running: false, status: 'SUCCESS', sourceCount: 3, signalCount: 5, eventCount: 1 }
  },
  '/api/news/categories': [],
  '/api/research-radar/events/10?section=OVERVIEW': {
    event: dashboardRadarEvent,
    signals: [],
    evidence: [],
    agentTrace: [],
    timeline: [],
    observations: [],
    researchLinks: [],
    workspaceState: { eventId: 10, read: true, followed: false, disposition: 'ACTIVE' },
    trust: {
      independentSourceCount: 3,
      sourceTierCounts: { OFFICIAL: 1, MEDIA: 2 },
      citationCoveredCount: 0,
      citationTotalCount: 0,
      concentration: '来源分布均衡',
      conflicts: [],
      limitation: '仍需跟踪政策传导效果'
    },
    interpretation: {
      eventId: 10,
      status: 'SUCCESS',
      stale: false,
      result: {
        factSummary: '央行宣布下调存款准备金率。',
        newDevelopment: '释放中长期流动性。',
        whyItMatters: '可能影响资金利率、信贷与风险偏好。',
        impactChain: ['降准', '流动性释放', '资产定价变化'],
        uncertainties: ['实体融资需求仍待验证'],
        nextObservations: ['观察资金利率与新增信贷'],
        evidenceRefs: []
      }
    }
  },
  '/api/knowledge/overview': {
    actions: [],
    activeTopics: [],
    recentEntries: [],
    acceptedTaskCount: 0,
    suggestedTaskCount: 0,
    dueReviewCount: 0,
    activeTopicCount: 1
  },
  '/api/knowledge/topics?page=0&size=100&lifecycle=ACTIVE': {
    items: [{
      id: 41,
      name: '实际利率下行有利于黄金定价',
      description: '持续检验降息预期、实际利率与黄金资金流。',
      lifecycleStatus: 'ACTIVE',
      masteryStatus: 'REVIEWING',
      revision: 2,
      articleCount: 0
    }],
    totalCount: 1,
    page: 0,
    pageSize: 100,
    totalPages: 1
  },
  '/api/knowledge/investment-recognitions': [{
    id: 81,
    status: 'ACCEPTED',
    topicId: 41,
    revision: 1
  }],
  '/api/knowledge/topics/41': {
    topic: {
      id: 41,
      name: '实际利率下行有利于黄金定价',
      description: '持续检验降息预期、实际利率与黄金资金流。',
      lifecycleStatus: 'ACTIVE',
      masteryStatus: 'REVIEWING',
      revision: 2,
      articleCount: 0
    },
    events: [{ id: 1, canonicalTitle: '美联储降息预期升温，黄金ETF出现增量资金' }],
    evidence: [{
      id: 1,
      eventId: 1,
      sourceTier: 'MEDIA',
      evidenceType: 'FACT',
      claim: '黄金ETF单周流入12亿美元。',
      confidence: 75,
      articleTitle: '黄金ETF单周流入12亿美元',
      articleUrl: 'https://example.com/gold-etf'
    }],
    tasks: [],
    entries: []
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
  '/api/evidence': [
    { id: 1, eventId: 1, sourceTier: 'MEDIA', evidenceType: 'DATA', claim: '黄金ETF单周流入12亿美元。', confidence: 75 },
    { id: 2, eventId: 1, sourceTier: 'REGULATOR', evidenceType: 'TIMELINE', claim: '美联储官员释放偏鸽措辞。', confidence: 90 },
    { id: 3, eventId: 2, sourceTier: 'COMPANY', evidenceType: 'FACT', claim: '官方文档公布了多代理工作流能力。', confidence: 85 }
  ],
  '/api/research/runs': [
    {
      id: 1,
      runDate: '2026-06-29',
      themeCodes: ['china_macro'],
      sourceCount: 1,
      fetchedSourceCount: 1,
      articleCount: 1,
      eventCount: 1,
      evidenceCount: 1,
      learningTaskCount: 2,
      briefDate: '2026-06-25',
      status: 'COMPLETED',
      summary: 'sources=1, fetched=1, articles=1, events=1, evidence=1, learningTasks=2'
    }
  ],
  '/api/research/runs/1': {
    run: {
      id: 1,
      runDate: '2026-06-29',
      themeCodes: ['china_macro'],
      sourceCount: 1,
      fetchedSourceCount: 1,
      articleCount: 1,
      eventCount: 1,
      evidenceCount: 1,
      learningTaskCount: 2,
      briefDate: '2026-06-25',
      status: 'COMPLETED',
      summary: 'sources=1, fetched=1, articles=1, events=1, evidence=1, learningTasks=2'
    },
    plannedSources: [
      {
        sourceId: 1,
        sourceName: 'Macro Source',
        sourceTier: 'OFFICIAL',
        themeCodes: ['china_macro'],
        credibility: 5,
        enabled: true
      }
    ],
    planSteps: [
      {
        id: 1,
        researchRunId: 1,
        stepId: 'plan_sources',
        title: '规划来源',
        stepType: 'PLANNING',
        executor: 'SourcePlanner',
        status: 'COMPLETED',
        outputSummary: 'plannedSources=1',
        attempt: 0,
        maxAttempts: 1,
        progressDelta: 1
      },
      {
        id: 2,
        researchRunId: 1,
        stepId: 'fetch_sources',
        title: '抓取来源',
        stepType: 'FETCH',
        executor: 'FetchService',
        status: 'COMPLETED',
        outputSummary: 'fetchedSources=1, errors=0',
        attempt: 1,
        maxAttempts: 1,
        progressDelta: 1
      }
    ],
    agentRuns: [
      { id: 1, researchRunId: 1, nodeName: 'source-fetch', status: 'SUCCESS', durationMs: 12 },
      {
        id: 2,
        researchRunId: 1,
        nodeName: 'evidence-extract',
        status: 'FALLBACK',
        durationMs: 4,
        fallbackUsed: true,
        fallbackReason: 'LLM_UNCONFIGURED',
        errorType: 'LLM_UNCONFIGURED',
        terminationReason: 'FALLBACK'
      },
      { id: 3, researchRunId: 1, nodeName: 'research-orchestrate', status: 'COMPLETED', durationMs: 40 }
    ]
  },
  '/api/agent-runs': [
    { id: 1, nodeName: 'brief-generate', status: 'SUCCESS', durationMs: 12 }
  ]
};

beforeEach(() => {
  const state = JSON.parse(JSON.stringify(responses)) as Record<string, any>;
  state['/api/events/paged?page=0&pageSize=100'] = {
    items: state['/api/events'], totalCount: state['/api/events'].length, page: 0, pageSize: 100, totalPages: 1
  };
  state['/api/evidence/paged?page=0&pageSize=200'] = {
    items: state['/api/evidence'], totalCount: state['/api/evidence'].length, page: 0, pageSize: 200, totalPages: 1
  };
  vi.stubGlobal('confirm', vi.fn(() => true));
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/research/runs' && init?.method === 'POST') {
      const run = {
        id: 2,
        runDate: JSON.parse(String(init.body)).runDate,
        themeCodes: JSON.parse(String(init.body)).themeCodes,
        sourceCount: 3,
        fetchedSourceCount: 0,
        articleCount: 0,
        eventCount: 0,
        evidenceCount: 0,
        learningTaskCount: 0,
        status: 'RUNNING',
        summary: 'Planned 3 sources for themes: 中国宏观, AI 创业'
      };
      state['/api/research/runs'] = [run, ...state['/api/research/runs']];
      const plannedSources = [
        {
          sourceId: 1,
          sourceName: 'Macro Source',
          sourceTier: 'OFFICIAL',
          themeCodes: ['china_macro'],
          credibility: 5,
          enabled: true
        }
      ];
      const planSourceStep = {
        id: 3,
        researchRunId: 2,
        stepId: 'plan_sources',
        title: '规划来源',
        stepType: 'PLANNING',
        executor: 'SourcePlanner',
        status: 'COMPLETED',
        outputSummary: 'plannedSources=3',
        attempt: 0,
        maxAttempts: 1,
        progressDelta: 3
      };
      const runningFetchStep = {
        id: 4,
        researchRunId: 2,
        stepId: 'fetch_sources',
        title: '抓取来源',
        stepType: 'FETCH',
        executor: 'FetchService',
        status: 'RUNNING',
        outputSummary: 'fetchedSources=0, errors=0',
        attempt: 1,
        maxAttempts: 1,
        progressDelta: 0
      };
      const completedRun = {
        ...run,
        fetchedSourceCount: 3,
        articleCount: 4,
        eventCount: 2,
        evidenceCount: 5,
        learningTaskCount: 3,
        briefDate: '2026-06-25',
        status: 'COMPLETED',
        summary: 'sources=3, fetched=3, articles=4, events=2, evidence=5, learningTasks=3'
      };
      state['/api/research/runs/2'] = {
        reads: 0,
        runningDetail: {
          run,
          plannedSources,
          planSteps: [planSourceStep, runningFetchStep],
          agentRuns: []
        },
        completedDetail: {
          run: completedRun,
          plannedSources,
          planSteps: [
            planSourceStep,
            {
              ...runningFetchStep,
              status: 'COMPLETED',
              outputSummary: 'fetchedSources=3, errors=0',
              progressDelta: 3
            }
          ],
          agentRuns: [
            {
              id: 5,
              researchRunId: 2,
              nodeName: 'evidence-extract',
              status: 'FALLBACK',
              durationMs: 14,
              fallbackUsed: true,
              fallbackReason: 'LLM_UNCONFIGURED',
              errorType: 'LLM_UNCONFIGURED',
              terminationReason: 'FALLBACK'
            },
            { id: 4, researchRunId: 2, nodeName: 'research-orchestrate', status: 'COMPLETED', durationMs: 88 }
          ]
        }
      };
      return mockApiResponse(run);
    }
    if (url === '/api/research/runs/2' && state[url]) {
      const progress = state[url];
      progress.reads += 1;
      const detail = progress.reads < 2 ? progress.runningDetail : progress.completedDetail;
      if (progress.reads >= 2) {
        state['/api/research/runs'] = [
          detail.run,
          ...state['/api/research/runs'].filter((item: { id: number }) => item.id !== detail.run.id)
        ];
      }
      return mockApiResponse(detail);
    }
    return mockApiResponse(state[url] ?? (url.startsWith('/api/investment-reactions/changes?') ? [] : {}));
  }));
});

afterEach(() => {
  vi.useRealTimers();
});

test('renders the FinScope workspace shell and dashboard', async () => {
  render(<App />);

  expect(screen.getByText('FinScope')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '今天的研究脉冲' })).toBeInTheDocument();
});

test('opens the full Agent workspace from the primary navigation', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Agent' }));

  expect(screen.getByRole('heading', { name: 'Agent · 全域问答' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '配置 FinScope Agent' })).toBeInTheDocument();
  expect(document.querySelector('main.workspace')).toHaveClass('workspace--agent');
});

test('dashboard keeps the research pulse and replaces operational sections with market summaries', async () => {
  render(<App />);

  expect(await screen.findByRole('heading', { name: '今天的研究脉冲' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '市场概况' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '优先处理' })).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '板块动向' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '工作区概览' })).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '自选股速览' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: '运行账本' })).not.toBeInTheDocument();
});

test('dashboard opens the market pulse workspace from its market summary', async () => {
  const originalFetch = vi.mocked(fetch).getMockImplementation()!;
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    if (String(input) === '/api/market-pulse/latest') {
      return mockApiResponse({ qualityStatus: 'UNAVAILABLE', sectors: [] });
    }
    if (String(input) === '/api/market-pulse/dates') {
      return mockApiResponse([]);
    }
    return originalFetch(input, init);
  });
  render(<App />);

  await userEvent.click(await screen.findByRole('button', { name: '查看市场状态' }));

  expect(screen.getByRole('heading', { name: 'Market Pulse · 市场机会' })).toBeInTheDocument();
});

test('dashboard hotspot opens the research radar workspace', async () => {
  render(<App />);

  await userEvent.click(await screen.findByRole('button', { name: /央行宣布下调存款准备金率/ }));

  expect(screen.getByText('News Wire · 市场资讯')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '研究雷达' })).toHaveAttribute('aria-pressed', 'true');
  expect(await screen.findByRole('dialog', { name: '央行宣布下调存款准备金率' })).toBeInTheDocument();
});

test('opens investment observation as an independent top-level workspace', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: '投资观察' }));

  expect(await screen.findByRole('heading', { name: '投资观察' })).toBeInTheDocument();
  expect(screen.getByText('Investment Observation · 投资观察')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '当前没有符合条件的跟踪事件' })).toBeInTheDocument();
});

test('dashboard uses a responsive research command layout', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.dashboard-pulse\s*{[^}]*grid-template-columns:/s);
  expect(styles).toMatch(/\.dashboard-command\s*{[^}]*min-width:\s*0/s);
  expect(styles).toMatch(/\.dashboard-command\s*{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/s);
  expect(styles).toMatch(/\.dashboard-pulse\s*{[^}]*gap:\s*52px/s);
  expect(styles).toMatch(/\.dashboard-pulse\s*{[^}]*padding:\s*36px\s+40px/s);
  expect(styles).toMatch(/\.dashboard-pulse-item:active\s*{[^}]*transform:\s*scale\(0\.985\)/s);
  expect(styles).toMatch(/\.dashboard-hotspots\s+\.dashboard-section-heading\s*{[^}]*align-items:\s*start[^}]*flex-direction:\s*column/s);
  expect(styles).toMatch(/\.dashboard-hotspots\s+\.dashboard-section-heading\s*>\s*p\s*{[^}]*max-width:\s*720px[^}]*text-align:\s*left/s);
  expect(styles).toMatch(/@media \(max-width: 760px\)[\s\S]*\.dashboard-pulse[\s\S]*grid-template-columns:\s*1fr/s);
});

test('opens the unified knowledge workbench without globally loading learning tasks', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Facts & Knowledge' }));

  expect(await screen.findByRole('heading', { name: '今天哪些变化，值得修正我的判断？' })).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith('/api/knowledge/overview', expect.any(Object));
  expect(fetch).not.toHaveBeenCalledWith('/api/topics', expect.anything());
  expect(fetch).not.toHaveBeenCalledWith('/api/learning-tasks', expect.anything());
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

test('keeps briefs independent from investment recognition', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Briefs' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看简报' }));

  expect(screen.queryByRole('button', { name: '沉淀主题' })).not.toBeInTheDocument();
  expect(await screen.findByRole('button', { name: '返回简报列表' })).toBeInTheDocument();
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

test('facts and knowledge verifies a proposition only when it affects a recognition', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Facts & Knowledge' }));
  await userEvent.click(await screen.findByRole('button', { name: '核验队列' }));

  expect(await screen.findByRole('heading', { name: '黄金ETF单周流入12亿美元。' })).toBeInTheDocument();
  expect(screen.getAllByText('实际利率下行有利于黄金定价').length).toBeGreaterThan(0);
  expect(screen.getByText('需要找到公告、监管或公司一手材料')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Events' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Evidence' })).not.toBeInTheDocument();
});

test('agent runs table shows node start time', () => {
  render(
    <AgentRunsView
      agentRuns={[
        {
          id: 1,
          nodeName: 'brief-generate',
          status: 'SUCCESS',
          durationMs: 12,
          createdAt: '2026-06-29T09:16:42'
        }
      ]}
    />
  );

  const table = screen.getByRole('table');
  expect(table.closest('.agent-runs-panel')).toBeTruthy();
  expect(within(table).getByText('开始时间')).toBeInTheDocument();
  expect(within(table).getByText('2026-06-29 09:16')).toBeInTheDocument();
});


test('brief reader shows research evidence and learning tasks', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Briefs' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看简报' }));

  expect(await screen.findByText('研究上下文')).toBeInTheDocument();
  expect(screen.getByText('中国宏观 / FOLLOW_UP')).toBeInTheDocument();
  expect(screen.getByText('央行开展3000亿元MLF操作并下调利率10个基点')).toBeInTheDocument();
  expect(screen.getByText(/\[MEDIA\].*黄金ETF单周流入12亿美元。/)).toBeInTheDocument();
  expect(screen.getByText('为什么实际利率下行会推升黄金配置需求？')).toBeInTheDocument();
});

test('research workbench runs a full research job and shows agent trace', async () => {
  const user = userEvent.setup();
  render(<App />);

  await user.click(screen.getByRole('button', { name: 'Research' }));

  expect(await screen.findByText('启动探索性研究')).toBeInTheDocument();
  expect(screen.getByText('历次研究运行')).toBeInTheDocument();
  expect(screen.getByText('1/1')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '开始探索研究' }));

  expect(fetch).toHaveBeenCalledWith('/api/research/runs', expect.objectContaining({
    method: 'POST',
    body: expect.stringContaining('"themeCodes"')
  }));
  expect(await screen.findAllByText('研究运行已启动，正在同步进度')).not.toHaveLength(0);
  await user.click(await screen.findByText('研究过程与来源'));
  expect(screen.getByText('执行步骤')).toBeInTheDocument();
  expect(screen.getByText('来源快照')).toBeInTheDocument();
  expect(screen.getByText('规划来源')).toBeInTheDocument();
  expect(screen.getByText('抓取来源')).toBeInTheDocument();
  expect(screen.getByText('Macro Source')).toBeInTheDocument();
  expect(screen.getAllByText('运行中').length).toBeGreaterThan(0);

  expect(await screen.findByText('提取证据')).toBeInTheDocument();
  expect(screen.getAllByText('LLM_UNCONFIGURED').length).toBeGreaterThan(0);
  expect(await screen.findByText('research orchestrate')).toBeInTheDocument();
  expect(screen.getAllByText('已完成').length).toBeGreaterThan(0);
});

test('dark theme keeps ghost buttons visually subordinate', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\[data-theme="dark"\]\s+\.ghost-button\s*{[^}]*background:/s);
  expect(styles).toMatch(/\[data-theme="dark"\]\s+\.ghost-button:hover:not\(:disabled\)\s*{/s);
  expect(styles).toMatch(/\[data-theme="dark"\]\s+\.ghost-button:focus-visible\s*{/s);
  expect(styles).not.toMatch(/\[data-theme="dark"\]\s+\.ghost-button\s*{[^}]*background:\s*(?:#fff(?:fff)?|white)\b/is);
});

test('renders dashboard data while an unrelated startup request is pending', async () => {
  window.history.replaceState({}, '', '/');
  const originalFetch = vi.mocked(fetch).getMockImplementation()!;
  vi.mocked(fetch).mockImplementation((input, init) => {
    if (String(input) === '/api/agent-runs') {
      return new Promise<Response>(() => {});
    }
    return originalFetch(input, init);
  });
  render(<App />);

  expect(await screen.findByText('央行宣布下调存款准备金率')).toBeInTheDocument();
});
