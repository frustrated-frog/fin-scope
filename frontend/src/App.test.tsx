import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import App from './App';
import { AgentRunsView } from './features/agents/AgentRunsView';
import { ArticleCard } from './features/articles/ArticleCard';
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
  '/api/strategy/overview': { holdings: [], targetWeight: 0, currentWeight: 0 },
  '/api/strategy/playbooks': [],
  '/api/strategy/stock-theses': [],
  '/api/strategy/reviews': [],
  '/api/dashboard': {
    sourceCount: 2,
    articleCount: 3,
    briefCount: 1,
    latestFetchRuns: [],
    hotspotRankings: [
      {
        categoryCode: 'FINANCE',
        label: '金融',
        items: [{ ...dashboardRadarEvent, lifecycleState: dashboardRadarEvent.hotspotLifecycleState }]
      },
      { categoryCode: 'TECHNOLOGY', label: '科技', items: [] },
      { categoryCode: 'POLITICS', label: '政治', items: [] }
    ]
  },
  '/api/research-radar?category=ALL&watchlistOnly=false&limit=20&state=ALL': {
    overview: { eventCount: 1, highPriorityCount: 1, watchlistRelatedCount: 0, sourceCount: 3 },
    events: [dashboardRadarEvent],
    latestChanges: [dashboardRadarEvent],
    warnings: [],
    refreshedAt: '2026-08-06T09:30:00',
    productionStatus: { running: false, status: 'SUCCESS', sourceCount: 3, signalCount: 5, eventCount: 1 }
  },
  '/api/news/categories': [],
  '/api/research-radar/events/10': {
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
  '/api/sources': [
    {
      id: 1,
      name: '测试财经RSS',
      type: 'RSS',
      url: 'https://example.com/rss',
      enabled: true,
      fetchFrequencyMinutes: 60,
      credibility: 4,
      tags: '宏观',
      maxItemsPerRun: 5,
      scheduleTimes: '08:30',
      scheduledEnabled: true
    }
  ],
  '/api/intake/batches': [
    {
      id: 1,
      sourceId: 1,
      sourceName: '测试财经RSS',
      triggerType: 'MANUAL',
      status: 'COMPLETED',
      startedAt: '2026-07-09T08:30:00',
      endedAt: '2026-07-09T08:30:05',
      lookbackDays: 3,
      maxItemsRequested: 5,
      rawItemCount: 3,
      candidateCount: 2,
      agentReviewedCount: 2,
      duplicateCount: 0,
      lowValueCount: 0,
      batchSummaryText: '本批共 2 条候选，优先看美联储政策与 AI Agent 工作流。'
    }
  ],
  '/api/intake/candidates?status=PENDING': [
    {
      id: 1,
      batchId: 1,
      sourceId: 1,
      sourceName: '测试财经RSS',
      sourceType: 'RSS',
      originalTitle: 'Fed signals cuts',
      originalUrl: 'https://example.com/fed',
      originalSummary: 'Fed officials discussed rate cuts.',
      chineseTitle: '美联储释放降息信号',
      decisionSummary: '值得入库：这是影响黄金、美元和风险偏好的高相关宏观信号。',
      keyFactsJson: '["美联储官员释放偏鸽表述","市场重新定价降息预期"]',
      whyItMatters: '会影响利率预期、黄金定价和权益风险偏好。',
      noveltyJudgment: '与已有降息交易主题相关，但有新的政策表述。',
      riskFlagsJson: '["需要核对原文语境"]',
      agentScore: 86,
      agentRecommendation: 'PROMOTABLE',
      agentReason: '宏观相关性强，且具备后续跟踪价值。',
      agentStatus: 'FALLBACK',
      humanStatus: 'PENDING'
    }
  ],
  '/api/intake/candidates?status=SKIPPED': [],
  '/api/intake/candidates?status=SAVED_FOR_LATER': [],
  '/api/intake/candidates?status=PROMOTED': [],
  '/api/intake/candidates?status=REJECTED': [],
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
        cardMarkdown: '## 情报卡片',
        analysisSections: [
          { title: '政策/事件脉络', content: '美联储释放偏鸽信号，市场开始重新评估降息节奏。' },
          { title: '市场反应', content: '黄金走强，美元和美债收益率承压，权益风险偏好改善。' },
          { title: '下一观察窗口', content: '继续观察通胀数据、议息会议和就业数据。' }
        ]
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
          cardMarkdown: '## 情报卡片',
          analysisSections: [
            { title: '政策/事件脉络', content: '美联储释放偏鸽信号，市场开始重新评估降息节奏。' },
            { title: '市场反应', content: '黄金走强，美元和美债收益率承压，权益风险偏好改善。' },
            { title: '下一观察窗口', content: '继续观察通胀数据、议息会议和就业数据。' }
          ]
        }
      }
    ],
    totalCount: 1,
    page: 0,
    pageSize: 20,
    totalPages: 1
  },
  '/api/articles/ingest-url': {
    taskId: 'task-manual',
    status: 'QUEUED',
    phase: 'QUEUED',
    message: '等待开始'
  },
  '/api/sources/1/intake-fetch-async': {
    taskId: 'task-intake',
    status: 'QUEUED',
    phase: 'QUEUED',
    message: '等待抓取信息源'
  },
  '/api/tasks/task-intake': {
    taskId: 'task-intake',
    status: 'COMPLETED',
    phase: 'COMPLETED',
    message: '候选池已更新：2 条候选'
  },
  '/api/intake/candidates/1/promote-async': {
    taskId: 'task-promote-1',
    status: 'QUEUED',
    phase: 'QUEUED',
    message: '等待入文章库'
  },
  '/api/tasks/task-promote-1': {
    taskId: 'task-promote-1',
    status: 'COMPLETED',
    phase: 'COMPLETED',
    message: '已入文章库 #3；研究工作包已生成：事件 #1，美联储释放降息信号，证据 1 条，学习任务 3 个，选题 2 个',
    articleId: 3
  },
  '/api/tasks/task-manual': {
    taskId: 'task-manual',
    status: 'COMPLETED',
    phase: 'COMPLETED',
    message: '情报卡片已生成，已加入文章列表',
    articleId: 2,
    article: { id: 2, title: '粘贴 URL 生成情报卡片', sourceName: '手动研究', noveltyType: 'NEW', category: '市场' }
  },
  '/api/tasks/task-fail': {
    taskId: 'task-fail',
    status: 'FAILED',
    phase: 'FAILED',
    errorMessage: '未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页'
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
  '/api/content-ideas/paged?page=0&pageSize=8': {
    items: [
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
    totalCount: 9,
    page: 0,
    pageSize: 8,
    totalPages: 2
  },
  '/api/content-ideas/paged?page=1&pageSize=8': {
    items: [],
    totalCount: 9,
    page: 1,
    pageSize: 8,
    totalPages: 2
  },
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
      contentIdeaCount: 1,
      briefDate: '2026-06-25',
      status: 'COMPLETED',
      summary: 'sources=1, fetched=1, articles=1, events=1, evidence=1, learningTasks=2, contentIdeas=1'
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
      contentIdeaCount: 1,
      briefDate: '2026-06-25',
      status: 'COMPLETED',
      summary: 'sources=1, fetched=1, articles=1, events=1, evidence=1, learningTasks=2, contentIdeas=1'
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
    if (url === '/api/articles/ingest-url' && String(init?.body).includes('x-shell')) {
      return mockApiResponse({ taskId: 'task-fail', status: 'QUEUED', phase: 'QUEUED', message: '等待开始' });
    }
    if (url === '/api/content-ideas/1/status' && init?.method === 'POST') {
      const payload = JSON.parse(String(init.body));
      state['/api/content-ideas'][0].status = payload.status;
      return mockApiResponse(state['/api/content-ideas'][0]);
    }
    if (url === '/api/intake/candidates/1/status' && init?.method === 'POST') {
      const payload = JSON.parse(String(init.body));
      const [candidate] = state['/api/intake/candidates?status=PENDING'];
      candidate.humanStatus = payload.humanStatus;
      state['/api/intake/candidates?status=PENDING'] = [];
      state[`/api/intake/candidates?status=${payload.humanStatus}`] = [candidate];
      return mockApiResponse(candidate);
    }
    if (url === '/api/sources/1/intake-fetch' && init?.method === 'POST') {
      return mockApiResponse(state['/api/intake/batches'][0]);
    }
    if (url === '/api/sources/1' && init?.method === 'DELETE') {
      state['/api/sources'] = state['/api/sources'].filter((source: { id: number }) => source.id !== 1);
      return mockApiResponse(null);
    }
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
        contentIdeaCount: 0,
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
        contentIdeaCount: 2,
        briefDate: '2026-06-25',
        status: 'COMPLETED',
        summary: 'sources=3, fetched=3, articles=4, events=2, evidence=5, learningTasks=3, contentIdeas=2'
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
    return mockApiResponse(state[url] ?? {});
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

test('dashboard presents the research command sections from loaded workspace data', async () => {
  render(<App />);

  expect(await screen.findByRole('heading', { name: '今天的研究脉冲' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '优先处理' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '工作区概览' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: '运行账本' })).toBeInTheDocument();
});

test('dashboard directs the event priority item to the current market news workspace', async () => {
  render(<App />);

  await userEvent.click(await screen.findByRole('button', { name: '查看研究流' }));

  expect(screen.getByText('News Wire · 市场资讯')).toBeInTheDocument();
});

test('dashboard hotspot opens the research radar workspace', async () => {
  render(<App />);

  await userEvent.click(await screen.findByRole('button', { name: /央行宣布下调存款准备金率/ }));

  expect(screen.getByText('News Wire · 市场资讯')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '研究雷达' })).toHaveAttribute('aria-pressed', 'true');
  expect(await screen.findByRole('dialog', { name: '央行宣布下调存款准备金率' })).toBeInTheDocument();
});

test('dashboard uses a responsive research command layout', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.dashboard-pulse\s*{[^}]*grid-template-columns:/s);
  expect(styles).toMatch(/\.dashboard-workspace-grid\s*{[^}]*grid-template-columns:/s);
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

test('topbar separates data readouts, controls and system status', async () => {
  render(<App />);

  expect(await screen.findByText('Articles')).toBeInTheDocument();

  const topbarActions = document.querySelector('.topbar-actions') as HTMLElement;
  const topbar = within(topbarActions);
  const readouts = topbar.getByRole('group', { name: '数据概览' });
  const themeButton = topbar.getByRole('button', { name: '切换为浅色模式' });
  const refreshButton = topbar.getByRole('button', { name: '刷新' });
  const systemStatus = topbar.getByRole('status', { name: '系统状态' });

  expect(within(readouts).getByText('Articles')).toBeInTheDocument();
  expect(within(readouts).getByText('Topics')).toBeInTheDocument();
  expect(within(readouts).getByLabelText('主题数量 1')).toBeInTheDocument();
  expect(themeButton).toHaveClass('topbar-control', 'theme-toggle');
  expect(refreshButton).toHaveClass('topbar-control', 'topbar-refresh');
  expect(systemStatus).toHaveTextContent('系统状态');
  expect(systemStatus).toHaveTextContent('准备就绪');
  expect(themeButton.querySelector('svg')).toBeTruthy();
  expect(refreshButton.querySelector('svg')).toBeTruthy();
  expect(themeButton).not.toHaveTextContent('浅色');
});

test('theme toggle keeps the document root in sync with the active theme', async () => {
  render(<App />);

  expect(await screen.findByText('Articles')).toBeInTheDocument();
  expect(document.documentElement.dataset.theme).toBe('dark');

  await userEvent.click(screen.getByRole('button', { name: '切换为浅色模式' }));

  expect(document.documentElement.dataset.theme).toBe('light');
});

test('sources workspace exposes intake configuration and manual candidate fetch', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Sources' }));

  expect(await screen.findByRole('heading', { name: '建立稳定、可信的信息入口' })).toBeInTheDocument();
  expect(screen.getByLabelText('信息源健康概览')).toBeInTheDocument();
  expect(screen.getByRole('group', { name: '信息源状态筛选' })).toBeInTheDocument();
  expect(screen.getByRole('searchbox', { name: '搜索信息源' })).toBeInTheDocument();
  expect(await screen.findByLabelText('每次抓取条数')).toBeInTheDocument();
  expect(screen.getByLabelText('每天抓取时间')).toBeInTheDocument();
  expect(screen.getByLabelText('开启定时抓取')).toBeInTheDocument();
  expect(screen.getByRole('option', { name: '网页列表' })).toBeInTheDocument();
  expect(screen.getByText('5 条/次')).toBeInTheDocument();
  expect(screen.getByText('08:30')).toBeInTheDocument();

  const sourceCard = (await screen.findByText('测试财经RSS')).closest('.source-item') as HTMLElement;
  await userEvent.click(within(sourceCard).getByRole('button', { name: '抓取' }));

  expect(fetch).toHaveBeenCalledWith('/api/sources/1/intake-fetch-async', expect.objectContaining({ method: 'POST' }));
  expect(fetch).not.toHaveBeenCalledWith('/api/sources/1/fetch', expect.objectContaining({ method: 'POST' }));
});

test('sources workspace filters the directory without changing configured sources', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Sources' }));
  const search = await screen.findByRole('searchbox', { name: '搜索信息源' });

  await userEvent.type(search, '不存在的来源');
  expect(screen.getByText('没有匹配的信息源')).toBeInTheDocument();
  expect(screen.queryByText('测试财经RSS')).not.toBeInTheDocument();

  await userEvent.clear(search);
  await userEvent.click(screen.getByRole('button', { name: '停用' }));
  expect(screen.getByText('没有匹配的信息源')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '启用' }));
  expect(await screen.findByText('测试财经RSS')).toBeInTheDocument();
  expect(fetch).not.toHaveBeenCalledWith('/api/sources', expect.objectContaining({ method: 'POST' }));
});

test('sources workspace confirms and permanently deletes a source', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Sources' }));
  const sourceCard = (await screen.findByText('测试财经RSS')).closest('.source-item') as HTMLElement;
  await userEvent.click(within(sourceCard).getByRole('button', { name: '删除-1' }));

  expect(screen.getByRole('dialog', { name: '删除信息源' })).toHaveTextContent('已抓取的文章和研究历史仍会保留');
  expect(fetch).not.toHaveBeenCalledWith('/api/sources/1', expect.objectContaining({ method: 'DELETE' }));

  await userEvent.click(screen.getByRole('button', { name: '确认删除' }));

  expect(fetch).toHaveBeenCalledWith('/api/sources/1', expect.objectContaining({ method: 'DELETE' }));
  await waitFor(() => expect(screen.queryByText('测试财经RSS')).not.toBeInTheDocument());
  expect(await screen.findByText('信息源已删除')).toBeInTheDocument();
});

test('sources workspace shows failed intake batches as errors', async () => {
  vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/sources/1/intake-fetch-async' && init?.method === 'POST') {
      return mockApiResponse({
        taskId: 'task-intake-failed',
        status: 'QUEUED',
        phase: 'QUEUED',
        message: '等待抓取信息源'
      });
    }
    if (url === '/api/tasks/task-intake-failed') {
      return mockApiResponse({
        taskId: 'task-intake-failed',
        status: 'FAILED',
        phase: 'FAILED',
        errorMessage: '没有产出候选内容'
      });
    }
    return mockApiResponse(
      (JSON.parse(JSON.stringify(responses)) as Record<string, unknown>)[url] ?? {}
    );
  });

  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Sources' }));
  const sourceCard = (await screen.findByText('测试财经RSS')).closest('.source-item') as HTMLElement;
  await userEvent.click(within(sourceCard).getByRole('button', { name: '抓取' }));

  expect(await screen.findByText('没有产出候选内容')).toBeInTheDocument();
  expect(screen.queryByText('已抓取到候选池')).not.toBeInTheDocument();
});

test('intake workspace shows agent-reviewed Chinese candidates and promotes to articles', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Intake' }));

  expect(await screen.findByText('候选池')).toBeInTheDocument();
  expect(screen.getByText('美联储释放降息信号')).toBeInTheDocument();
  expect(screen.getByText('86')).toBeInTheDocument();
  expect(screen.getByText('值得入库：这是影响黄金、美元和风险偏好的高相关宏观信号。')).toBeInTheDocument();
  expect(screen.getByText('美联储官员释放偏鸽表述')).toBeInTheDocument();
  expect(screen.getByText('本批共 2 条候选，优先看美联储政策与 AI Agent 工作流。')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '入文章库-1' }));

  expect(fetch).toHaveBeenCalledWith('/api/intake/candidates/1/promote-async', expect.objectContaining({ method: 'POST' }));
  expect(fetch).toHaveBeenCalledWith('/api/tasks/task-promote-1', expect.any(Object));
  expect(await screen.findByText('已入文章库 #3')).toBeInTheDocument();
  expect(screen.getAllByText('已入文章库 #3；研究工作包已生成：事件 #1，美联储释放降息信号，证据 1 条，学习任务 3 个，选题 2 个').length).toBeGreaterThan(0);
});

test('intake workspace supports saving candidates for later review', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Intake' }));

  expect(await screen.findByRole('button', { name: '稍后看' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: '稍后看-1' }));

  expect(fetch).toHaveBeenCalledWith('/api/intake/candidates/1/status', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ humanStatus: 'SAVED_FOR_LATER' })
  }));
  expect(await screen.findByText('候选项已标记为 SAVED_FOR_LATER')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '稍后看' }));

  expect(fetch).toHaveBeenCalledWith('/api/intake/candidates?status=SAVED_FOR_LATER', expect.any(Object));
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
    body: JSON.stringify({
      url: 'https://example.com/article',
      sourceName: '手动研究',
      tags: '市场',
      category: '市场'
    })
  }));
});

test('shows readable error when pasted url cannot be extracted', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.type(await screen.findByPlaceholderText('输入文章URL...'), 'https://x.com/x-shell');
  await userEvent.click(screen.getByRole('button', { name: '生成情报卡片' }));

  expect(await screen.findAllByText('未能读取到可用正文：该页面更像是登录/JavaScript 渲染壳页')).not.toHaveLength(0);
});

test('article insight renders category-aware sections', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.click(await screen.findByText('美联储释放降息信号 黄金走强'));

  expect(await screen.findByText('分类解读')).toBeInTheDocument();
  expect(screen.getByText('政策/事件脉络')).toBeInTheDocument();
  expect(screen.getByText('市场反应')).toBeInTheDocument();
  expect(screen.getByText('下一观察窗口')).toBeInTheDocument();
  expect(screen.getByText('美联储释放偏鸽信号，市场开始重新评估降息节奏。')).toBeInTheDocument();
});

test('market article insight derives category-aware sections from legacy fields', () => {
  render(
    <ArticleCard
      article={{
        id: 99,
        title: '国新办发布会释放循环经济政策信号',
        url: 'https://example.com/policy-briefing',
        sourceName: '新闻发布会',
        category: '市场',
        insightCard: {
          oneSentenceSummary: '发布会明确循环经济十五五规划方向。',
          coreEvent: '国新办发布会介绍循环经济规划。',
          importance: '政策可能改变资源循环产业链预期。',
          impactTargets: '地方政府、回收企业、新能源车产业链',
          keyData: '主要再生资源回收利用量超过4.1亿吨。',
          riskFactors: '落地节奏和地方执行力度仍需验证。',
          futureOutlook: '后续关注清单、行业标准和财政金融支持。',
          followUpQuestions: '下一场发布会、配套细则和地方试点。'
        }
      }}
      isExpanded
      onToggle={vi.fn()}
      onDelete={vi.fn()}
      categoryColor="#f0b90b"
    />
  );

  expect(screen.getByText('分类解读')).toBeInTheDocument();
  expect(screen.getByText('政策/事件脉络')).toBeInTheDocument();
  expect(screen.getByText('发布会/公告要点')).toBeInTheDocument();
  expect(screen.getByText('市场反应')).toBeInTheDocument();
  expect(screen.getByText('下一观察窗口')).toBeInTheDocument();
  expect(screen.queryByText('深度解读')).not.toBeInTheDocument();
  expect(screen.queryByText('背景是什么')).not.toBeInTheDocument();
});

test('does not offer article compounding in the independent article library', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Article' }));
  await userEvent.click(await screen.findByText('美联储释放降息信号 黄金走强'));

  expect(screen.queryByRole('button', { name: '沉淀到主题库' })).not.toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: '文章情报台' })).toBeInTheDocument();
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
  const articleCard = sourceTag.closest('.article-card') as HTMLElement;
  const categoryTag = within(articleCard).getByText('宏观');

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

test('content studio shows idea score and outline for generated topics', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Studio' }));

  expect(await screen.findByText('为什么市场还没等到降息，黄金已经先涨了？')).toBeInTheDocument();
  expect(screen.getByText('84')).toBeInTheDocument();
  expect(screen.getByText('X 长帖')).toBeInTheDocument();
  expect(screen.getByText('证据强度够高，而且能沉淀成长期有效的宏观解释框架。')).toBeInTheDocument();
  expect(screen.getByText(/1\. 先看降息预期/)).toBeInTheDocument();
});

test('content studio loads ideas through backend pagination', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Studio' }));

  expect(await screen.findByText('为什么市场还没等到降息，黄金已经先涨了？')).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith('/api/content-ideas/paged?page=0&pageSize=8', expect.any(Object));
  expect(screen.getByText('第 1 / 2 页')).toBeInTheDocument();
  expect(screen.getByText('共 9 个选题')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '下一页' }));

  expect(fetch).toHaveBeenCalledWith('/api/content-ideas/paged?page=1&pageSize=8', expect.any(Object));
  expect(await screen.findByText('当前筛选下暂无选题。')).toBeInTheDocument();
  expect(screen.getByText('第 2 / 2 页')).toBeInTheDocument();
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

test('agent runs view refreshes itself while visible', async () => {
  const state = JSON.parse(JSON.stringify(responses)) as Record<string, any>;
  let agentRunRequests = 0;
  vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/agent-runs') {
      agentRunRequests += 1;
      return mockApiResponse(agentRunRequests > 1
        ? [{ id: 2, nodeName: 'article-interpret', status: 'SUCCESS', durationMs: 88, createdAt: '2026-07-08T22:10:00' }]
        : []);
    }
    return mockApiResponse(state[url] ?? {});
  });

  render(<App />);

  await screen.findByText('Articles');
  await userEvent.click(screen.getByRole('button', { name: 'Agent Runs' }));

  expect(await screen.findByText('article-interpret')).toBeInTheDocument();
  expect(agentRunRequests).toBeGreaterThan(1);
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
