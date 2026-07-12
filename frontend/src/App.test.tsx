import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
// @ts-expect-error Vitest runs in Node, while the app intentionally avoids shipping Node types.
import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import App from './App';
import { AgentRunsView } from './features/agents/AgentRunsView';
import { ArticleCard } from './features/articles/ArticleCard';

const responses: Record<string, unknown> = {
  '/api/strategy/overview': { holdings: [], targetWeight: 0, currentWeight: 0 },
  '/api/strategy/playbooks': [],
  '/api/strategy/stock-theses': [],
  '/api/strategy/reviews': [],
  '/api/dashboard': { sourceCount: 2, articleCount: 3, briefCount: 1, latestFetchRuns: [] },
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
      {
        id: 1,
        title: '美联储释放降息信号 黄金走强',
        url: 'https://x.com/tester/status/123',
        sourceName: '测试财经RSS',
        noveltyType: 'NEW'
      }
    ],
    linkedBriefs: [
      { id: 1, briefDate: '2026-06-23', title: 'FinScope Daily Brief - 2026-06-23', markdownPath: 'data/vault/daily-briefs/2026-06-23.md' }
    ],
    markdown: '# 降息交易\n\n- 状态：LEARNING\n- 描述：跟踪利率预期如何影响黄金和风险资产。\n\n## 关键术语\n\n- 美联储\n- 实际利率\n- 黄金\n\n## 学习问题\n\n- 为什么降息会影响黄金？\n- 如何判断预期差？\n\n## 关联文章\n\n- [美联储释放降息信号 黄金走强](https://x.com/tester/status/123)\n\n## 文章解读\n\n### 一句话摘要\n\n美联储释放偏鸽信号，市场重新交易降息预期。'
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
    {
      eventId: 1,
      articleId: 1,
      relationType: 'PRIMARY',
      matchScore: 1,
      noveltyType: 'NEW',
      noveltyReason: '首次进入事件记忆',
      articleTitle: '美联储释放降息信号 黄金走强',
      articleUrl: 'https://example.com/1'
    },
    {
      eventId: 1,
      articleId: 2,
      relationType: 'SUPPORTING',
      matchScore: 0.88,
      noveltyType: 'FOLLOW_UP',
      noveltyReason: '命中历史事件，包含新的数据、时间线或市场反应',
      articleTitle: '黄金ETF单周流入12亿美元',
      articleUrl: 'https://example.com/2'
    }
  ],
  '/api/events/1/evidence': [
    { id: 1, eventId: 1, sourceTier: 'MEDIA', evidenceType: 'DATA', claim: '黄金ETF单周流入12亿美元。', confidence: 75 },
    { id: 2, eventId: 1, sourceTier: 'REGULATOR', evidenceType: 'TIMELINE', claim: '美联储官员释放偏鸽措辞。', confidence: 90 }
  ],
  '/api/events/2/articles': [
    {
      eventId: 2,
      articleId: 3,
      relationType: 'PRIMARY',
      matchScore: 1,
      noveltyType: 'NEW',
      noveltyReason: '首次进入事件记忆',
      articleTitle: 'Claude Code 推出新的多代理工作流',
      articleUrl: 'https://example.com/3'
    }
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
  vi.stubGlobal('confirm', vi.fn(() => true));
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/articles/ingest-url' && String(init?.body).includes('x-shell')) {
      return {
        ok: true,
        json: async () => ({ taskId: 'task-fail', status: 'QUEUED', phase: 'QUEUED', message: '等待开始' })
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
    if (url === '/api/events/1/status' && init?.method === 'POST') {
      const payload = JSON.parse(String(init.body));
      state['/api/events'][0].status = payload.status;
      return {
        ok: true,
        json: async () => state['/api/events'][0]
      } as Response;
    }
    if (url === '/api/events/2/merge' && init?.method === 'POST') {
      state['/api/events'][1].status = 'ARCHIVED';
      return {
        ok: true,
        json: async () => state['/api/events'][0]
      } as Response;
    }
    if (url === '/api/events/1/articles/2/move' && init?.method === 'POST') {
      return {
        ok: true,
        json: async () => ({ ...state['/api/events'][1], articleCount: 2 })
      } as Response;
    }
    if (url === '/api/intake/candidates/1/promote' && init?.method === 'POST') {
      const [candidate] = state['/api/intake/candidates?status=PENDING'];
      candidate.humanStatus = 'PROMOTED';
      candidate.promotedArticleId = 3;
      state['/api/intake/candidates?status=PENDING'] = [];
      state['/api/intake/candidates?status=PROMOTED'] = [candidate];
      return {
        ok: true,
        json: async () => ({
          candidateId: 1,
          articleId: 3,
          status: 'PROMOTED',
          workflowStatus: 'SUCCESS',
          eventId: 1,
          eventTitle: '美联储释放降息信号',
          evidenceCount: 1,
          learningTaskCount: 3,
          contentIdeaCount: 2,
          workflowSummary: '研究工作包已生成：事件 #1，美联储释放降息信号，证据 1 条，学习任务 3 个，选题 2 个'
        })
      } as Response;
    }
    if (url === '/api/intake/candidates/1/status' && init?.method === 'POST') {
      const payload = JSON.parse(String(init.body));
      const [candidate] = state['/api/intake/candidates?status=PENDING'];
      candidate.humanStatus = payload.humanStatus;
      state['/api/intake/candidates?status=PENDING'] = [];
      state[`/api/intake/candidates?status=${payload.humanStatus}`] = [candidate];
      return {
        ok: true,
        json: async () => candidate
      } as Response;
    }
    if (url === '/api/sources/1/intake-fetch' && init?.method === 'POST') {
      return {
        ok: true,
        json: async () => state['/api/intake/batches'][0]
      } as Response;
    }
    if (url === '/api/topics/1' && init?.method === 'DELETE') {
      state['/api/topics'] = state['/api/topics'].filter((topic: { id: number }) => topic.id !== 1);
      return {
        ok: true,
        json: async () => ({})
      } as Response;
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
      return {
        ok: true,
        json: async () => run
      } as Response;
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
      return {
        ok: true,
        json: async () => detail
      } as Response;
    }
    return {
      ok: true,
      json: async () => state[url] ?? {}
    } as Response;
  }));
});

afterEach(() => {
  vi.useRealTimers();
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

test('sources workspace exposes intake configuration and manual candidate fetch', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Sources' }));

  expect(await screen.findByLabelText('每次抓取条数')).toBeInTheDocument();
  expect(screen.getByLabelText('每天抓取时间')).toBeInTheDocument();
  expect(screen.getByLabelText('开启定时抓取')).toBeInTheDocument();
  expect(screen.getByRole('option', { name: '网页列表' })).toBeInTheDocument();
  expect(screen.getByText('5 条/次')).toBeInTheDocument();
  expect(screen.getByText('08:30')).toBeInTheDocument();

  const sourceCard = (await screen.findByText('测试财经RSS')).closest('.source-item') as HTMLElement;
  await userEvent.click(within(sourceCard).getByRole('button', { name: '抓取' }));

  expect(fetch).toHaveBeenCalledWith('/api/sources/1/intake-fetch', expect.objectContaining({ method: 'POST' }));
  expect(fetch).not.toHaveBeenCalledWith('/api/sources/1/fetch', expect.objectContaining({ method: 'POST' }));
});

test('sources workspace shows failed intake batches as errors', async () => {
  vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url === '/api/sources/1/intake-fetch' && init?.method === 'POST') {
      return {
        ok: true,
        json: async () => ({
          id: 2,
          sourceId: 1,
          sourceName: '测试财经RSS',
          status: 'FAILED',
          candidateCount: 0,
          errorMessage: '没有产出候选内容'
        })
      } as Response;
    }
    return {
      ok: true,
      json: async () => (JSON.parse(JSON.stringify(responses)) as Record<string, unknown>)[url] ?? {}
    } as Response;
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

  expect(fetch).toHaveBeenCalledWith('/api/intake/candidates/1/promote', expect.objectContaining({ method: 'POST' }));
  expect(await screen.findByText('已入文章库 #3')).toBeInTheDocument();
  expect(screen.getByText('已入文章库 #3；研究工作包已生成：事件 #1，美联储释放降息信号，证据 1 条，学习任务 3 个，选题 2 个')).toBeInTheDocument();
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
      onCompound={vi.fn()}
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
  expect(screen.getAllByText('FOLLOW_UP').length).toBeGreaterThan(0);
  expect(screen.getByText(/证据 2/)).toBeInTheDocument();
  expect(screen.getByText(/文章 2/)).toBeInTheDocument();
});

test('events view can filter evidence by source tier and shows novelty distribution', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Events' }));

  expect(await screen.findByText('FOLLOW_UP 1')).toBeInTheDocument();
  expect(screen.getByText('NEW 1')).toBeInTheDocument();
  const detail = screen.getByRole('region', { name: '事件详情' });
  expect(within(detail).getAllByText('MEDIA').length).toBeGreaterThan(0);
  expect(within(detail).getByText('黄金ETF单周流入12亿美元。')).toBeInTheDocument();
  expect(within(detail).getAllByText('REGULATOR').length).toBeGreaterThan(0);
  expect(within(detail).getByText('美联储官员释放偏鸽措辞。')).toBeInTheDocument();

  await userEvent.selectOptions(within(detail).getByLabelText('证据来源层级'), 'REGULATOR');

  expect(within(detail).getAllByText('REGULATOR').length).toBeGreaterThan(0);
  expect(within(detail).getByText('美联储官员释放偏鸽措辞。')).toBeInTheDocument();
  expect(within(detail).queryByText('黄金ETF单周流入12亿美元。')).not.toBeInTheDocument();
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
      return {
        ok: true,
        json: async () => agentRunRequests > 1
          ? [{ id: 2, nodeName: 'article-interpret', status: 'SUCCESS', durationMs: 88, createdAt: '2026-07-08T22:10:00' }]
          : []
      } as Response;
    }
    return {
      ok: true,
      json: async () => state[url] ?? {}
    } as Response;
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

  expect(await screen.findByText('生成今日研究')).toBeInTheDocument();
  expect(screen.getByText('研究运行记录')).toBeInTheDocument();
  expect(screen.getByText('1/1')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: '运行研究' }));

  expect(fetch).toHaveBeenCalledWith('/api/research/runs', expect.objectContaining({
    method: 'POST',
    body: expect.stringContaining('"themeCodes"')
  }));
  expect(await screen.findAllByText('研究运行已启动，正在同步进度')).not.toHaveLength(0);
  expect(await screen.findByText('计划来源')).toBeInTheDocument();
  expect(screen.getByText('Plan steps')).toBeInTheDocument();
  expect(screen.getByText('规划来源')).toBeInTheDocument();
  expect(screen.getByText('抓取来源')).toBeInTheDocument();
  expect(screen.getByText('Macro Source')).toBeInTheDocument();
  expect(screen.getAllByText('RUNNING').length).toBeGreaterThan(0);

  expect(await screen.findByText('fallback: LLM_UNCONFIGURED')).toBeInTheDocument();
  expect(await screen.findByText('research-orchestrate')).toBeInTheDocument();
  expect(screen.getAllByText('COMPLETED').length).toBeGreaterThan(0);
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

test('topics keep detail action on the left and learning status on the right', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Topics' }));

  const topicCard = (await screen.findByText('降息交易')).closest('article');
  expect(topicCard).toBeTruthy();

  const actionRow = within(topicCard as HTMLElement).getByRole('group', { name: '主题操作' });
  const detailButton = within(actionRow).getByRole('button', { name: '查看详情' });
  const deleteButton = within(actionRow).getByRole('button', { name: '删除主题' });
  const statusBadge = within(actionRow).getByText('LEARNING');

  expect(actionRow).toHaveClass('topic-card-actions');
  expect(deleteButton).toHaveClass('compact-button');
  expect(deleteButton).toHaveClass('topic-delete-button');
  expect(detailButton.compareDocumentPosition(statusBadge) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
});

test('topics delete with an in-app confirmation dialog', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Topics' }));

  const topicCard = (await screen.findByText('降息交易')).closest('article');
  expect(topicCard).toBeTruthy();
  await userEvent.click(within(topicCard as HTMLElement).getByRole('button', { name: '删除主题' }));

  expect(confirm).not.toHaveBeenCalled();
  expect(fetch).not.toHaveBeenCalledWith('/api/topics/1', expect.objectContaining({ method: 'DELETE' }));
  const dialog = await screen.findByRole('dialog', { name: '删除主题' });
  expect(within(dialog).getByText('降息交易')).toBeInTheDocument();
  expect(within(dialog).getByText('关联文章、简报和原始内容不会被删除。')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: '确认删除' }));

  expect(fetch).toHaveBeenCalledWith('/api/topics/1', expect.objectContaining({ method: 'DELETE' }));
  expect(await screen.findByText('0 topics')).toBeInTheDocument();
  expect(screen.queryByText('降息交易')).not.toBeInTheDocument();
});

test('topics open a full-width markdown topic reader', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Topics' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看详情' }));

  expect(fetch).toHaveBeenCalledWith('/api/topics/1', expect.anything());
  expect(await screen.findByText('Topic Reader')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '返回主题库' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '记录理解' })).toBeInTheDocument();
  expect(screen.getByRole('region', { name: '主题详情正文' })).toHaveClass('topic-reader-document');
  expect(screen.getByRole('complementary', { name: '主题上下文' })).toHaveClass('topic-reader-context');
  expect(screen.getAllByText('关键术语').length).toBeGreaterThan(0);
  expect(screen.getByText('文章解读')).toBeInTheDocument();
  expect(screen.queryByText((content, element) => (
    element?.tagName === 'PRE' && content.includes('# 降息交易')
  ))).not.toBeInTheDocument();
});

test('topic reader can jump to the learning note form for the same topic', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Topics' }));
  await userEvent.click(await screen.findByRole('button', { name: '查看详情' }));
  await userEvent.click(await screen.findByRole('button', { name: '记录理解' }));

  expect(await screen.findByRole('heading', { name: 'Learning', level: 2 })).toBeInTheDocument();
  expect(screen.getByLabelText('个人理解')).toBeInTheDocument();
  expect(screen.queryByRole('region', { name: '主题详情正文' })).not.toBeInTheDocument();
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

test('learning queue filter has its own row above task cards', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Learning' }));

  const filter = await screen.findByLabelText('学习主题筛选');
  const heading = filter.closest('.learning-queue-heading');
  expect(heading).toBeTruthy();
  expect(filter.closest('label')).toHaveClass('learning-queue-filter');
});

test('learning task actions keep status update, note entry and event jump in one row', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Learning' }));

  const taskCard = (await screen.findByText('为什么实际利率下行会推升黄金配置需求？')).closest('article');
  expect(taskCard).toBeTruthy();

  const actionRow = within(taskCard as HTMLElement).getByRole('group', { name: '学习任务操作-1' });

  expect(actionRow).toHaveClass('learning-task-actions');
  expect(within(actionRow).getByRole('button', { name: '更新任务状态-1' })).toBeInTheDocument();
  expect(within(actionRow).getByRole('button', { name: '记录学习-1' })).toBeInTheDocument();
  expect(within(actionRow).getByRole('button', { name: '查看关联事件-1' })).toBeInTheDocument();
});

test('learning task note action opens the right-side note form from the current page', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Learning' }));
  await userEvent.click(await screen.findByRole('button', { name: '记录学习-1' }));

  expect(fetch).toHaveBeenCalledWith('/api/topics/1', expect.anything());
  expect(await screen.findByRole('heading', { name: '降息交易' })).toBeInTheDocument();
  expect(screen.getByLabelText('个人理解')).toBeInTheDocument();
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

test('events view presents the selected event as a structured detail panel', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Events' }));

  const detail = await screen.findByRole('region', { name: '事件详情' });

  expect(within(detail).getByRole('heading', { name: '美联储降息预期升温，黄金ETF出现增量资金' })).toBeInTheDocument();
  expect(within(detail).getByText('市场重新交易实际利率下行与黄金定价。')).toBeInTheDocument();
  expect(within(detail).getByText('重要性')).toBeInTheDocument();
  expect(within(detail).getByText('86')).toBeInTheDocument();
  expect(within(detail).getAllByText('关联文章').length).toBeGreaterThan(0);
  expect(within(detail).getByText('2 篇')).toBeInTheDocument();
  expect(within(detail).getAllByText('事件证据').length).toBeGreaterThan(0);
  expect(within(detail).getByText('2 条')).toBeInTheDocument();
  expect(within(detail).getByLabelText('证据来源层级')).toBeInTheDocument();
});

test('events workbench explains timeline, merge basis, evidence strength and event outputs', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Events' }));

  const detail = await screen.findByRole('region', { name: '事件详情' });

  expect(screen.getByText('事件研究台')).toBeInTheDocument();
  expect(within(detail).getByText('事件时间线')).toBeInTheDocument();
  expect(within(detail).getByText('归并依据')).toBeInTheDocument();
  expect(within(detail).getByText('证据强度')).toBeInTheDocument();
  expect(within(detail).getByText('学习任务')).toBeInTheDocument();
  expect(within(detail).getByText('内容选题')).toBeInTheDocument();
  expect(within(detail).getByText(/匹配 88%/)).toBeInTheDocument();
  expect(within(detail).getByText('命中历史事件，包含新的数据、时间线或市场反应')).toBeInTheDocument();
  expect(within(detail).getByText('最高可信证据')).toBeInTheDocument();
  expect(within(detail).getByText('为什么实际利率下行会推升黄金配置需求？')).toBeInTheDocument();
  expect(within(detail).getByText('为什么市场还没等到降息，黄金已经先涨了？')).toBeInTheDocument();
});

test('events stylesheet prevents long article urls from widening the workbench', () => {
  const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
  const styles = readFileSync(`${cwd}/src/styles.css`, 'utf8');

  expect(styles).toMatch(/\.events-workbench\s*{[^}]*overflow-x:\s*hidden;/s);
  expect(styles).toMatch(/\.event-card\s*{[^}]*min-width:\s*0;/s);
  expect(styles).toMatch(/\.event-card-top\s+strong[\s\S]*overflow-wrap:\s*anywhere;/);
  expect(styles).toMatch(/\.event-timeline-list\s+strong[\s\S]*overflow-wrap:\s*anywhere;/);
});

test('events governance panel updates status, merges events and moves articles', async () => {
  render(<App />);

  await userEvent.click(screen.getByRole('button', { name: 'Events' }));

  const detail = await screen.findByRole('region', { name: '事件详情' });
  await userEvent.selectOptions(within(detail).getByLabelText('事件状态'), 'COOLING');
  await userEvent.click(within(detail).getByRole('button', { name: '保存事件状态' }));
  expect(fetch).toHaveBeenCalledWith('/api/events/1/status', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ status: 'COOLING' })
  }));

  await userEvent.selectOptions(within(detail).getByLabelText('合并到事件'), '2');
  await userEvent.click(within(detail).getByRole('button', { name: '合并事件' }));
  expect(fetch).toHaveBeenCalledWith('/api/events/1/merge', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ targetEventId: 2 })
  }));

  await userEvent.selectOptions(within(detail).getByLabelText('移动文章-2'), 'NEW_EVENT');
  await userEvent.click(within(detail).getByRole('button', { name: '移动文章-2' }));
  expect(fetch).toHaveBeenCalledWith('/api/events/1/articles/2/move', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({ createNewEvent: true })
  }));
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
