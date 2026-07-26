import { useEffect, useMemo, useState } from 'react';

import { AppShell } from './app/AppShell';
import { AgentRunsView } from './features/agents/AgentRunsView';
import { ArticleView } from './features/articles/ArticleView';
import { BriefReaderView } from './features/briefs/BriefReaderView';
import { BriefsView } from './features/briefs/BriefsView';
import { ContentStudioView } from './features/content-studio/ContentStudioView';
import { DashboardView } from './features/dashboard/DashboardView';
import { EvidenceView } from './features/evidence/EvidenceView';
import { EventsView } from './features/events/EventsView';
import { FinancialsView } from './features/financials/FinancialsView';
import { IntakeView } from './features/intake/IntakeView';
import { MarketIntelView } from './features/market-intel/MarketIntelView';
import { KnowledgeView } from './features/knowledge/KnowledgeView';
import type { KnowledgeOverview } from './features/knowledge/knowledgeTypes';
import { ResearchView } from './features/research/ResearchView';
import { SettingsView } from './features/settings/SettingsView';
import { SourcesView } from './features/sources/SourcesView';
import { WatchlistView } from './features/watchlist/WatchlistView';
import { StrategyView } from './features/strategy/StrategyView';
import { QuantResearchEntryIntent } from './features/strategy/quantTypes';
import { api } from './shared/api/client';
import {
  AgentRun,
  Article,
  Brief,
  BriefResearchContext,
  ContentIdea,
  Dashboard,
  EvidenceItem,
  EventCluster,
  FetchBatch,
  IntakeCandidate,
  LearningTask,
  PageResponse,
  ResearchRun,
  ResearchRunDetail,
  ResearchEvaluation,
  ResearchReport,
  ResearchThesis,
  Source,
  ToastItem,
  View
} from './shared/types';

const AGENT_RUN_REFRESH_INTERVAL_MS = 3000;
const RESEARCH_RUN_REFRESH_INTERVAL_MS = 750;
const CONTENT_IDEA_PAGE_SIZE = 8;
const RESEARCH_ACTIVE_STATUSES = new Set(['RUNNING']);

function isResearchRunActive(status?: string) {
  return Boolean(status && RESEARCH_ACTIVE_STATUSES.has(status));
}

export default function App() {
  const [view, setView] = useState<View>(() => (
    new URLSearchParams(window.location.search).has('section') ? 'knowledge' : 'dashboard'
  ));
  const [theme, setTheme] = useState<'light' | 'dark'>('dark');
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [sources, setSources] = useState<Source[]>([]);
  const [articles, setArticles] = useState<Article[]>([]);
  const [fetchBatches, setFetchBatches] = useState<FetchBatch[]>([]);
  const [intakeCandidates, setIntakeCandidates] = useState<IntakeCandidate[]>([]);
  const [intakeStatus, setIntakeStatus] = useState('PENDING');
  const [briefs, setBriefs] = useState<Brief[]>([]);
  const [selectedBrief, setSelectedBrief] = useState<Brief | null>(null);
  const [selectedBriefContext, setSelectedBriefContext] = useState<BriefResearchContext | null>(null);
  const [events, setEvents] = useState<EventCluster[]>([]);
  const [evidenceItems, setEvidenceItems] = useState<EvidenceItem[]>([]);
  const [focusedEventId, setFocusedEventId] = useState<number | null>(null);
  const [activeTopicCount, setActiveTopicCount] = useState(0);
  const [learningTasks, setLearningTasks] = useState<LearningTask[]>([]);
  const [contentIdeas, setContentIdeas] = useState<ContentIdea[]>([]);
  const [contentIdeaPage, setContentIdeaPage] = useState<PageResponse<ContentIdea> | null>(null);
  const [contentIdeaPageIndex, setContentIdeaPageIndex] = useState(0);
  const [researchRuns, setResearchRuns] = useState<ResearchRun[]>([]);
  const [researchTheses, setResearchTheses] = useState<ResearchThesis[]>([]);
  const [researchRunDetail, setResearchRunDetail] = useState<ResearchRunDetail | null>(null);
  const [researchReport, setResearchReport] = useState<ResearchReport | null>(null);
  const [researchBusy, setResearchBusy] = useState(false);
  const [researchReportBusy, setResearchReportBusy] = useState(false);
  const [compoundingBriefDates, setCompoundingBriefDates] = useState<Set<string>>(new Set());
  const [agentRuns, setAgentRuns] = useState<AgentRun[]>([]);
  const [message, setMessage] = useState('准备就绪');
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [quantResearchIntent, setQuantResearchIntent] = useState<QuantResearchEntryIntent>();

  const addToast = (toastMessage: string, type: 'success' | 'error' | 'info' = 'info') => {
    const id = Date.now();
    setToasts((current) => [...current, { id, message: toastMessage, type }]);
    setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id));
    }, 3000);
  };

  const upsertResearchRun = (run: ResearchRun) => {
    setResearchRuns((current) => [run, ...current.filter((item) => item.id !== run.id)]);
  };

  const refresh = async () => {
    const results = await Promise.allSettled([
      api<Dashboard>('/api/dashboard'),
      api<Source[]>('/api/sources'),
      api<Article[]>('/api/articles'),
      api<Brief[]>('/api/briefs'),
      api<EventCluster[]>('/api/events'),
      api<EvidenceItem[]>('/api/evidence'),
      api<KnowledgeOverview>('/api/knowledge/overview'),
      api<ContentIdea[]>('/api/content-ideas'),
      api<ResearchRun[]>('/api/research/runs'),
      api<ResearchThesis[]>('/api/research/theses'),
      api<AgentRun[]>('/api/agent-runs'),
      api<FetchBatch[]>('/api/intake/batches'),
      api<IntakeCandidate[]>(`/api/intake/candidates?status=${intakeStatus}`)
    ]);
    const value = <T,>(index: number): T | undefined => {
      const result = results[index];
      return result.status === 'fulfilled' ? result.value as T : undefined;
    };
    const dashboardData = value<Dashboard>(0); if (dashboardData) setDashboard(dashboardData);
    const sourceData = value<Source[]>(1); if (sourceData) setSources(sourceData);
    const articleData = value<Article[]>(2); if (articleData) setArticles(articleData);
    const briefData = value<Brief[]>(3); if (briefData) setBriefs(briefData);
    const eventData = value<EventCluster[]>(4); if (eventData) setEvents(eventData);
    const evidenceData = value<EvidenceItem[]>(5); if (evidenceData) setEvidenceItems(evidenceData);
    const knowledgeOverview = value<KnowledgeOverview>(6);
    if (knowledgeOverview) setActiveTopicCount(knowledgeOverview.activeTopicCount ?? 0);
    const contentIdeaData = value<ContentIdea[]>(7); if (contentIdeaData) setContentIdeas(contentIdeaData);
    const researchRunData = value<ResearchRun[]>(8); if (researchRunData) setResearchRuns(researchRunData);
    const researchThesisData = value<ResearchThesis[]>(9); if (Array.isArray(researchThesisData)) setResearchTheses(researchThesisData);
    const agentData = value<AgentRun[]>(10); if (agentData) setAgentRuns(agentData);
    const fetchBatchData = value<FetchBatch[]>(11); if (fetchBatchData) setFetchBatches(fetchBatchData);
    const intakeCandidateData = value<IntakeCandidate[]>(12); if (intakeCandidateData) setIntakeCandidates(intakeCandidateData);
    const failureCount = results.filter((result) => result.status === 'rejected').length;
    if (failureCount) {
      setMessage(`部分工作区数据刷新失败（${failureCount} 项），已保留已加载内容`);
    }
  };

  async function loadContentIdeaPage(page = contentIdeaPageIndex) {
    const nextPage = Math.max(0, page);
    const response = await api<PageResponse<ContentIdea>>(
      `/api/content-ideas/paged?page=${nextPage}&pageSize=${CONTENT_IDEA_PAGE_SIZE}`
    );
    setContentIdeaPage(response);
    setContentIdeaPageIndex(response.page);
  }

  useEffect(() => {
    refresh().catch((error) => setMessage(error instanceof Error ? error.message : '初始化失败'));
  }, []);

  useEffect(() => {
    if (view !== 'contentStudio') {
      return;
    }
    loadContentIdeaPage(contentIdeaPageIndex)
      .catch((error) => setMessage(error instanceof Error ? error.message : '选题分页加载失败'));
  }, [view, contentIdeaPageIndex]);

  useEffect(() => {
    if (view !== 'agents') {
      return undefined;
    }
    let cancelled = false;
    const loadAgentRuns = async () => {
      try {
        const agentData = await api<AgentRun[]>('/api/agent-runs');
        if (!cancelled) {
          setAgentRuns(agentData);
        }
      } catch (error) {
        if (!cancelled) {
          setMessage(error instanceof Error ? error.message : 'Agent Runs 刷新失败');
        }
      }
    };
    loadAgentRuns();
    const timer = window.setInterval(loadAgentRuns, AGENT_RUN_REFRESH_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [view]);

  useEffect(() => {
    const runId = researchRunDetail?.run?.id;
    const status = researchRunDetail?.run?.status;
    if (!runId || !isResearchRunActive(status)) {
      return undefined;
    }
    let cancelled = false;
    const loadProgress = async () => {
      try {
        const detail = await loadResearchRunProgress(runId);
        if (!cancelled && !isResearchRunActive(detail.run.status)) {
          setMessage(`研究运行完成：${detail.run.status}`);
          addToast(`研究运行完成：${detail.run.status}`, detail.run.status === 'FAILED' ? 'error' : 'success');
          refresh().catch((error) => setMessage(error instanceof Error ? error.message : '研究结果同步失败'));
        }
      } catch (error) {
        if (!cancelled) {
          setMessage(error instanceof Error ? error.message : '研究进度同步失败');
        }
      }
    };
    const timer = window.setInterval(loadProgress, RESEARCH_RUN_REFRESH_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [researchRunDetail?.run?.id, researchRunDetail?.run?.status]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.body.dataset.theme = theme;

    return () => {
      delete document.documentElement.dataset.theme;
      delete document.body.dataset.theme;
    };
  }, [theme]);

  const currentTitle = useMemo(() => {
    if (view === 'briefReader') {
      return 'Brief Reader';
    }
    switch (view) {
      case 'dashboard':
        return 'Dashboard';
      case 'sources':
        return 'Sources';
      case 'intake':
        return 'Intake';
      case 'article':
        return 'Article';
      case 'briefs':
        return 'Briefs';
      case 'research':
        return 'Research';
      case 'events':
        return 'Events';
      case 'eventDetail':
        return 'Event Archive';
      case 'evidence':
        return 'Evidence Ledger';
      case 'knowledge':
        return '知识工作台';
      case 'contentStudio':
        return 'Studio';
      case 'agents':
        return 'Agent Runs';
      case 'settings':
        return 'Settings';
      case 'watchlist':
        return 'Watchlist';
      case 'marketIntel':
        return 'Market Intel · 资金行为';
      case 'financials':
        return 'Financials · 公司财报';
      case 'strategy':
        return 'Strategy Workbench';
      default:
        return 'Dashboard';
    }
  }, [view]);

  function openEvent(eventId: number) {
    setFocusedEventId(eventId);
    setView('eventDetail');
  }

  async function openBrief(date: string) {
    setMessage('正在打开简报');
    try {
      const [detail, context] = await Promise.all([
        api<Brief>(`/api/briefs/${date}`),
        api<BriefResearchContext>(`/api/briefs/${date}/research-context`)
      ]);
      setSelectedBrief(detail);
      setSelectedBriefContext(context);
      setView('briefReader');
      setMessage('简报已打开');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '简报打开失败');
    }
  }

  async function loadResearchRunProgress(id: number) {
    const [detail, runs] = await Promise.all([
      api<ResearchRunDetail>(`/api/research/runs/${id}`),
      api<ResearchRun[]>('/api/research/runs')
    ]);
    setResearchRunDetail(detail);
    setResearchRuns(runs);
    return detail;
  }

  async function openResearchRun(id: number) {
    setResearchReport(null);
    const detail = await loadResearchRunProgress(id);
    if (isResearchRunActive(detail.run.status)) {
      setMessage('研究运行已启动，正在同步进度');
    }
    return detail;
  }

  async function openResearchReport(id: number) {
    setResearchReportBusy(true);
    setMessage('正在打开研究报告');
    try {
      const report = await api<ResearchReport>(`/api/research/runs/${id}/report`);
      setResearchReport(report);
      setMessage('研究报告已打开');
    } catch (error) {
      const message = error instanceof Error ? error.message : '研究报告打开失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setResearchReportBusy(false);
    }
  }

  async function regenerateResearchReport(id: number) {
    setResearchReportBusy(true);
    setMessage('正在补建研究报告');
    try {
      const report = await api<ResearchReport>(`/api/research/runs/${id}/report/regenerate`, { method: 'POST' });
      setResearchReport(report);
      await loadResearchRunProgress(id);
      setMessage('研究报告已补建并打开');
      addToast('研究报告已补建完成', 'success');
    } catch (error) {
      const message = error instanceof Error ? error.message : '研究报告补建失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setResearchReportBusy(false);
    }
  }

  async function resumeResearchRun(id: number) {
    setResearchBusy(true);
    setMessage('正在从检查点恢复研究');
    try {
      await api<ResearchRun>(`/api/research/runs/${id}/resume`, { method: 'POST' });
      await loadResearchRunProgress(id);
      setMessage('研究已从检查点恢复');
      addToast('研究已从检查点恢复，正在继续执行', 'success');
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '研究恢复失败';
      setMessage(nextMessage);
      addToast(nextMessage, 'error');
    } finally {
      setResearchBusy(false);
    }
  }

  async function evaluateResearchRun(id: number) {
    setMessage('正在运行离线评测');
    try {
      const evaluation = await api<ResearchEvaluation>(`/api/research/runs/${id}/evaluations`, { method: 'POST' });
      await loadResearchRunProgress(id);
      setMessage(`离线评测完成：${evaluation.score} 分`);
      addToast(`离线评测 ${evaluation.score} 分 · ${evaluation.gateStatus}`, evaluation.gateStatus === 'PASS' ? 'success' : 'error');
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '离线评测失败';
      setMessage(nextMessage);
      addToast(nextMessage, 'error');
    }
  }

  async function runResearch(input: {
    thesisId?: number;
    runDate: string;
    themeCodes: string[];
    maxSourcesPerTheme: number;
    includeDisabled: boolean;
  }) {
    setResearchBusy(true);
    setMessage('正在启动研究运行');
    try {
      const run = await api<ResearchRun>('/api/research/runs', {
        method: 'POST',
        body: JSON.stringify(input)
      });
      upsertResearchRun(run);
      const detail = await openResearchRun(run.id);
      if (isResearchRunActive(detail.run.status)) {
        setMessage('研究运行已启动，正在同步进度');
        addToast('研究运行已启动，正在同步进度', 'info');
      } else {
        setMessage(`研究运行完成：${detail.run.status}`);
        addToast(`研究运行完成：${detail.run.status}`, detail.run.status === 'FAILED' ? 'error' : 'success');
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '研究运行失败';
      setMessage(message);
      addToast(message, 'error');
    } finally {
      setResearchBusy(false);
    }
  }

  async function createResearchThesis(input: Omit<ResearchThesis, 'id' | 'status' | 'createdAt' | 'updatedAt'>) {
    const thesis = await api<ResearchThesis>('/api/research/theses', {
      method: 'POST',
      body: JSON.stringify(input)
    });
    setResearchTheses((current) => [thesis, ...current.filter((item) => item.id !== thesis.id)]);
    addToast('研究命题已创建', 'success');
    return thesis;
  }

  async function refreshWorkspace() {
    setMessage('正在刷新');
    try {
      await refresh();
      setMessage('数据已同步');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '刷新失败');
    }
  }

  async function loadIntakeCandidates(status: string) {
    setIntakeStatus(status);
    const candidates = await api<IntakeCandidate[]>(`/api/intake/candidates?status=${status}`);
    setIntakeCandidates(candidates);
    return candidates;
  }

  async function compoundBriefToTopics(date: string) {
    if (compoundingBriefDates.has(date)) {
      return;
    }
    setCompoundingBriefDates((current) => new Set(current).add(date));
    setMessage('正在将简报沉淀到主题库');
    addToast('正在将简报沉淀到主题库', 'info');
    try {
      await api(`/api/topics/from-brief/${date}`, { method: 'POST' });
      await refresh();
      setMessage('简报已沉淀到主题库');
      addToast('简报已沉淀到主题库', 'success');
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '简报沉淀失败，请稍后重试';
      setMessage(errorMessage);
      addToast(errorMessage, 'error');
    } finally {
      setCompoundingBriefDates((current) => {
        const next = new Set(current);
        next.delete(date);
        return next;
      });
    }
  }

  async function updateContentIdeaStatus(ideaId: number, status: string) {
    await api(`/api/content-ideas/${ideaId}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
    setMessage(`选题状态已更新为 ${status}`);
    await refresh();
    if (view === 'contentStudio') {
      await loadContentIdeaPage(contentIdeaPageIndex);
    }
  }

  async function updateEventStatus(eventId: number, status: string) {
    await api<EventCluster>(`/api/events/${eventId}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
    setMessage(`事件状态已更新为 ${status}`);
    await refresh();
  }

  async function mergeEvent(sourceEventId: number, targetEventId: number) {
    await api<EventCluster>(`/api/events/${sourceEventId}/merge`, {
      method: 'POST',
      body: JSON.stringify({ targetEventId })
    });
    setMessage('事件已合并');
    await refresh();
  }

  async function moveEventArticle(sourceEventId: number, articleId: number, input: {
    targetEventId?: number;
    createNewEvent?: boolean;
  }) {
    await api<EventCluster>(`/api/events/${sourceEventId}/articles/${articleId}/move`, {
      method: 'POST',
      body: JSON.stringify(input)
    });
    setMessage('文章归属已调整');
    await refresh();
  }

  return (
    <AppShell
      view={view}
      currentTitle={currentTitle}
      theme={theme}
      articlesCount={articles.length}
      topicsCount={activeTopicCount}
      message={message}
      toasts={toasts}
      onChangeView={setView}
      onToggleTheme={() => setTheme((current) => current === 'dark' ? 'light' : 'dark')}
      onRefresh={refreshWorkspace}
    >
      {view === 'dashboard' && <DashboardView dashboard={dashboard} articles={articles} />}
      {view === 'sources' && (
        <SourcesView
          sources={sources}
          fetchBatches={fetchBatches}
          onChanged={refresh}
          addToast={addToast}
        />
      )}
      {view === 'intake' && (
        <IntakeView
          batches={fetchBatches}
          candidates={intakeCandidates}
          status={intakeStatus}
          onStatusChange={loadIntakeCandidates}
          onChanged={refresh}
          addToast={addToast}
        />
      )}
      {view === 'article' && (
        <ArticleView
          onWorkspaceChanged={refresh}
          addToast={addToast}
        />
      )}
      {view === 'briefs' && (
        <BriefsView
          briefs={briefs}
          onChanged={refresh}
          setMessage={setMessage}
          onOpenBrief={openBrief}
          onCompound={compoundBriefToTopics}
          compoundingBriefDates={compoundingBriefDates}
        />
      )}
      {view === 'briefReader' && (
        <BriefReaderView
          brief={selectedBrief}
          researchContext={selectedBriefContext}
          onBack={() => setView('briefs')}
          onCompound={compoundBriefToTopics}
          isCompounding={compoundingBriefDates.has(selectedBrief?.briefDate ?? '')}
        />
      )}
      {view === 'research' && (
        <ResearchView
          runs={researchRuns}
          theses={researchTheses}
          detail={researchRunDetail}
          report={researchReport}
          busy={researchBusy}
          reportBusy={researchReportBusy}
          onRun={runResearch}
          onCreateThesis={createResearchThesis}
          onOpenRun={openResearchRun}
          onOpenReport={openResearchReport}
          onRegenerateReport={regenerateResearchReport}
          onResumeRun={resumeResearchRun}
          onEvaluateRun={evaluateResearchRun}
          onCloseReport={() => setResearchReport(null)}
        />
      )}
      {view === 'events' && (
        <EventsView
          events={events}
          initialEventId={focusedEventId}
          mode="queue"
          onOpenEvent={openEvent}
          learningTasks={learningTasks}
          contentIdeas={contentIdeas}
          onContentIdeaStatusChange={updateContentIdeaStatus}
          onEventStatusChange={updateEventStatus}
          onMergeEvent={mergeEvent}
          onMoveEventArticle={moveEventArticle}
          onChanged={refresh}
          addToast={addToast}
        />
      )}
      {view === 'eventDetail' && (
        <EventsView
          events={events}
          initialEventId={focusedEventId}
          mode="detail"
          onOpenEvent={openEvent}
          onBack={() => setView('events')}
          learningTasks={learningTasks}
          contentIdeas={contentIdeas}
          onContentIdeaStatusChange={updateContentIdeaStatus}
          onEventStatusChange={updateEventStatus}
          onMergeEvent={mergeEvent}
          onMoveEventArticle={moveEventArticle}
          onChanged={refresh}
          addToast={addToast}
        />
      )}
      {view === 'evidence' && <EvidenceView evidenceItems={evidenceItems} events={events} onOpenEvent={(eventId) => {
        openEvent(eventId);
      }} />}
      {view === 'knowledge' && (
        <KnowledgeView addToast={addToast} setMessage={setMessage} />
      )}
      {view === 'contentStudio' && (
        <ContentStudioView
          contentIdeas={contentIdeaPage?.items ?? contentIdeas}
          pagination={contentIdeaPage}
          onPageChange={setContentIdeaPageIndex}
          onIdeaStatusChange={updateContentIdeaStatus}
          addToast={addToast}
        />
      )}
      {view === 'agents' && <AgentRunsView agentRuns={agentRuns} />}
      {view === 'settings' && <SettingsView setMessage={setMessage} />}
      {view === 'watchlist' && <WatchlistView addToast={addToast} setMessage={setMessage} />}
      {view === 'marketIntel' && <MarketIntelView addToast={addToast} setMessage={setMessage} onOpenQuantResearch={(intent) => { setQuantResearchIntent(intent); setView('strategy'); }} />}
      {view === 'financials' && <FinancialsView addToast={addToast} setMessage={setMessage} />}
      {view === 'strategy' && <StrategyView addToast={addToast} setMessage={setMessage} entryIntent={quantResearchIntent} onEntryIntentConsumed={() => setQuantResearchIntent(undefined)} />}
    </AppShell>
  );
}
